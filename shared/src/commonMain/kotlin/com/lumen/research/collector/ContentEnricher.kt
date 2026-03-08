package com.lumen.research.collector

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.Article_
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.delay
import net.dankito.readability4j.Readability4J

class ContentEnricher(
    private val httpClient: HttpClient,
    private val db: LumenDatabase,
) {

    suspend fun enrichAll(articles: List<Article>): Int {
        val toEnrich = articles.filter { needsEnrichment(it) }
        var enriched = 0

        for ((index, article) in toEnrich.withIndex()) {
            if (index > 0) delay(RATE_LIMIT_MS)
            try {
                val result = enrichSingle(article)
                if (result != null) enriched++
            } catch (_: Exception) {
                // Continue with next article on failure
            }
        }
        return enriched
    }

    suspend fun enrichSingle(article: Article): Article? {
        if (article.url.isBlank()) return null

        val html = try {
            val response = httpClient.get(article.url) {
                header("User-Agent", USER_AGENT)
                header("Accept", "text/html")
            }
            if (!response.status.isSuccess()) return null
            response.bodyAsText()
        } catch (_: Exception) {
            return null
        }

        val extractedContent = extractContent(article.url, html) ?: return null
        if (extractedContent.length < MIN_CONTENT_LENGTH) return null

        val updated = article.copy(content = extractedContent)
        db.articleBox.put(updated)
        return updated
    }

    suspend fun processUnenriched(): Int {
        val unenriched = db.articleBox.query()
            .equal(Article_.sourceType, "RSS", StringOrder.CASE_SENSITIVE)
            .build()
            .use { it.find() }
            .filter { needsEnrichment(it) }

        return enrichAll(unenriched)
    }

    internal fun needsEnrichment(article: Article): Boolean {
        return article.content.length < CONTENT_THRESHOLD &&
            article.url.isNotBlank() &&
            article.sourceType == "RSS"
    }

    internal fun extractContent(url: String, html: String): String? {
        return try {
            val readability = Readability4J(url, html)
            val parsed = readability.parse()
            parsed.content?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        internal const val RATE_LIMIT_MS = 1000L
        internal const val CONTENT_THRESHOLD = 200
        internal const val MIN_CONTENT_LENGTH = 100
        internal const val USER_AGENT =
            "Mozilla/5.0 (compatible; Lumen/1.0; +https://github.com/ydzat/lumen)"
    }
}
