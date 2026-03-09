package com.lumen.research.collector

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.Article_
import com.lumen.core.database.entities.Source
import io.objectbox.query.QueryBuilder.StringOrder
import com.prof18.rssparser.RssParser
import com.prof18.rssparser.RssParserBuilder
import com.prof18.rssparser.model.RssChannel
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class RssDataSource(
    private val db: LumenDatabase,
    private val rssParser: RssParser = createDefaultParser(),
    private val rssHubBaseUrl: String = DEFAULT_RSSHUB_BASE_URL,
) : DataSource {

    override val type: SourceType = SourceType.RSS
    override val displayName: String = "RSS"

    override suspend fun fetch(sources: List<Source>, context: FetchContext): DataFetchResult {
        val allArticles = mutableListOf<Article>()
        val errors = mutableListOf<String>()
        val failedIds = mutableSetOf<Long>()
        var remainingBudget = context.remainingBudget
        val existingUrls = queryExistingUrls()

        // Fair budget allocation: each source gets an equal share, preventing one source
        // from consuming the entire budget (e.g., OpenAI Blog returning 480+ items)
        val perSourceCap = if (sources.isNotEmpty()) {
            (context.remainingBudget / sources.size).coerceAtLeast(MIN_PER_SOURCE)
        } else {
            context.remainingBudget
        }

        for (source in sources) {
            if (remainingBudget <= 0) break

            try {
                val limit = remainingBudget.coerceAtMost(perSourceCap)
                val articles = fetchAndPersist(source, limit, existingUrls)
                allArticles.addAll(articles)
                remainingBudget -= articles.size
            } catch (e: Exception) {
                errors.add("RSS ${source.name}: ${e.message ?: e::class.simpleName}")
                failedIds.add(source.id)
            }
        }

        return DataFetchResult(allArticles, errors, SourceType.RSS, failedIds)
    }

    suspend fun fetchSingle(source: Source): List<Article> {
        return fetchAndPersist(source)
    }

    private suspend fun fetchAndPersist(
        source: Source,
        limit: Int = Int.MAX_VALUE,
        existingUrls: MutableSet<String> = mutableSetOf(),
    ): List<Article> {
        val feedUrl = resolveFeedUrl(source.url)
        val channel = rssParser.getRssChannel(feedUrl)
        val newArticles = processChannel(channel, source)
            .filter { it.url !in existingUrls }
            .take(limit)
        if (newArticles.isNotEmpty()) {
            db.articleBox.put(newArticles)
            newArticles.forEach { existingUrls.add(it.url) }
        }
        db.sourceBox.put(source.copy(lastFetchedAt = System.currentTimeMillis()))
        return newArticles
    }

    internal suspend fun parseAndProcess(xml: String, source: Source): List<Article> {
        val channel = rssParser.parse(xml)
        return processChannel(channel, source)
    }

    private fun processChannel(channel: RssChannel, source: Source): List<Article> {
        val now = System.currentTimeMillis()
        return channel.items.mapNotNull { item ->
            val itemUrl = item.link ?: return@mapNotNull null

            Article(
                sourceId = source.id,
                title = item.title.orEmpty(),
                url = itemUrl,
                summary = item.description.orEmpty(),
                content = item.content.orEmpty(),
                author = item.author.orEmpty(),
                publishedAt = parseRssDate(item.pubDate),
                fetchedAt = now,
                sourceType = "RSS",
            )
        }
    }

    internal fun resolveFeedUrl(url: String): String {
        return if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            "$rssHubBaseUrl$url"
        }
    }

    private fun queryExistingUrls(): MutableSet<String> {
        return db.articleBox.query()
            .notEqual(Article_.url, "", StringOrder.CASE_SENSITIVE)
            .equal(Article_.sourceType, "RSS", StringOrder.CASE_SENSITIVE)
            .build()
            .use { it.find() }
            .mapTo(mutableSetOf()) { it.url }
    }

    companion object {
        const val DEFAULT_RSSHUB_BASE_URL = "https://rsshub.app"
        const val MIN_PER_SOURCE = 5
        private const val USER_AGENT = "Mozilla/5.0 (compatible; Lumen/1.0; +https://github.com/ydzat/lumen)"

        fun createDefaultParser(): RssParser {
            val client = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .build()
                    chain.proceed(request)
                })
                .build()
            return RssParserBuilder(callFactory = client).build()
        }

        private val RFC_822_FORMAT = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)

        internal fun parseRssDate(dateStr: String?): Long {
            if (dateStr.isNullOrBlank()) return 0L
            return try {
                ZonedDateTime.parse(dateStr, RFC_822_FORMAT).toInstant().toEpochMilli()
            } catch (_: Exception) {
                try {
                    Instant.parse(dateStr).toEpochMilli()
                } catch (_: Exception) {
                    0L
                }
            }
        }
    }
}
