package com.lumen.research.collector

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.Source
import com.prof18.rssparser.RssParser
import com.prof18.rssparser.model.RssChannel
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class RssDataSource(
    private val db: LumenDatabase,
    private val rssParser: RssParser = RssParser(),
    private val rssHubBaseUrl: String = DEFAULT_RSSHUB_BASE_URL,
) : DataSource {

    override val type: SourceType = SourceType.RSS
    override val displayName: String = "RSS"

    override suspend fun fetch(sources: List<Source>, context: FetchContext): DataFetchResult {
        val allArticles = mutableListOf<Article>()
        val errors = mutableListOf<String>()
        var remainingBudget = context.remainingBudget

        for (source in sources) {
            if (remainingBudget <= 0) break

            try {
                val articles = fetchAndPersist(source, remainingBudget)
                allArticles.addAll(articles)
                remainingBudget -= articles.size
            } catch (e: Exception) {
                errors.add("RSS ${source.name}: ${e.message ?: e::class.simpleName}")
            }
        }

        return DataFetchResult(allArticles, errors, SourceType.RSS)
    }

    suspend fun fetchSingle(source: Source): List<Article> {
        return fetchAndPersist(source)
    }

    private suspend fun fetchAndPersist(source: Source, limit: Int = Int.MAX_VALUE): List<Article> {
        val feedUrl = resolveFeedUrl(source.url)
        val channel = rssParser.getRssChannel(feedUrl)
        val articles = processChannel(channel, source).take(limit)
        if (articles.isNotEmpty()) {
            db.articleBox.put(articles)
        }
        db.sourceBox.put(source.copy(lastFetchedAt = System.currentTimeMillis()))
        return articles
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

    companion object {
        const val DEFAULT_RSSHUB_BASE_URL = "https://rsshub.app"

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
