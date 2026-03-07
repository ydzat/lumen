package com.lumen.research.collector

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.Article_
import com.lumen.core.database.entities.Source
import com.lumen.core.database.entities.Source_
import com.prof18.rssparser.RssParser
import com.prof18.rssparser.model.RssChannel
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class RssCollector(
    private val db: LumenDatabase,
    private val rssParser: RssParser = RssParser(),
    private val rssHubBaseUrl: String = DEFAULT_RSSHUB_BASE_URL,
) {

    suspend fun fetchSource(source: Source): List<Article> {
        val feedUrl = resolveFeedUrl(source.url)
        val channel = rssParser.getRssChannel(feedUrl)
        return processChannel(channel, source)
    }

    suspend fun fetchAll(): List<Article> {
        val enabledSources = db.sourceBox.query()
            .equal(Source_.enabled, true)
            .build()
            .use { it.find() }
        return enabledSources.flatMap { source ->
            try {
                fetchSource(source)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    internal suspend fun parseAndProcess(xml: String, source: Source): List<Article> {
        val channel = rssParser.parse(xml)
        return processChannel(channel, source)
    }

    private fun processChannel(channel: RssChannel, source: Source): List<Article> {
        val now = System.currentTimeMillis()
        val existingUrls = queryExistingUrls(source.id)

        val newArticles = channel.items.mapNotNull { item ->
            val itemUrl = item.link ?: return@mapNotNull null
            if (itemUrl in existingUrls) return@mapNotNull null

            Article(
                sourceId = source.id,
                title = item.title.orEmpty(),
                url = itemUrl,
                summary = item.description.orEmpty(),
                content = item.content.orEmpty(),
                author = item.author.orEmpty(),
                publishedAt = parseRssDate(item.pubDate),
                fetchedAt = now,
            )
        }

        if (newArticles.isNotEmpty()) {
            db.articleBox.put(newArticles)
        }

        val updatedSource = source.copy(lastFetchedAt = now)
        db.sourceBox.put(updatedSource)

        return newArticles
    }

    private fun queryExistingUrls(sourceId: Long): Set<String> {
        val articles = db.articleBox.query()
            .equal(Article_.sourceId, sourceId)
            .build()
            .use { it.find() }
        return articles.mapTo(mutableSetOf()) { it.url }
    }

    internal fun resolveFeedUrl(url: String): String {
        return if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            "$rssHubBaseUrl${url}"
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
