package com.lumen.research.archiver

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.Article_
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

data class ArchiveResult(
    val archived: Int,
    val errors: List<String> = emptyList(),
)

data class ArchiveStats(
    val totalArticles: Long,
    val archivedArticles: Long,
    val activeArticles: Long,
)

class ArticleArchiver(
    private val db: LumenDatabase,
    private val httpClient: HttpClient? = null,
) {

    fun archiveStale(
        maxAgeDays: Int = DEFAULT_MAX_AGE_DAYS,
        minRelevanceScore: Float = DEFAULT_MIN_RELEVANCE,
    ): ArchiveResult {
        val cutoffTime = System.currentTimeMillis() - maxAgeDays.toLong() * MILLIS_PER_DAY
        val staleArticles = queryStaleArticles(cutoffTime, minRelevanceScore)

        val errors = mutableListOf<String>()
        var count = 0
        for (article in staleArticles) {
            try {
                db.articleBox.put(compressArticle(article))
                count++
            } catch (e: Exception) {
                errors.add("Failed to archive article ${article.id}: ${e.message}")
            }
        }

        return ArchiveResult(archived = count, errors = errors)
    }

    fun emergencyArchive(targetArticleCount: Int): ArchiveResult {
        val activeCount = countActiveArticles()
        if (activeCount <= targetArticleCount) return ArchiveResult(archived = 0)

        val toArchive = (activeCount - targetArticleCount).toInt()
        val scanLimit = toArchive.coerceAtMost(EMERGENCY_SCAN_LIMIT)
        val candidates = db.articleBox.query()
            .equal(Article_.archived, false)
            .equal(Article_.starred, false)
            .build()
            .use { it.find(0, (scanLimit * 2).toLong()) }
            .sortedBy { it.aiRelevanceScore }
            .take(toArchive)

        val errors = mutableListOf<String>()
        var count = 0
        for (article in candidates) {
            try {
                db.articleBox.put(compressArticle(article))
                count++
            } catch (e: Exception) {
                errors.add("Failed to archive article ${article.id}: ${e.message}")
            }
        }

        return ArchiveResult(archived = count, errors = errors)
    }

    suspend fun restore(articleId: Long): Article? {
        val article = db.articleBox.get(articleId) ?: return null
        if (!article.archived) return null

        var content = ""
        if (article.url.isNotBlank() && httpClient != null) {
            try {
                val response = httpClient.get(article.url)
                if (response.status.isSuccess()) {
                    content = response.bodyAsText()
                }
            } catch (_: Exception) {
                // Restore without content on fetch failure
            }
        }

        val restored = article.copy(
            archived = false,
            content = content,
        )
        db.articleBox.put(restored)
        return restored
    }

    fun needsEmergencyArchive(maxArticleCount: Int): Boolean {
        return countActiveArticles() > maxArticleCount
    }

    fun getArchiveStats(): ArchiveStats {
        val total = db.articleBox.count()
        val archived = db.articleBox.query()
            .equal(Article_.archived, true)
            .build()
            .use { it.count() }
        return ArchiveStats(
            totalArticles = total,
            archivedArticles = archived,
            activeArticles = total - archived,
        )
    }

    private fun compressArticle(article: Article): Article {
        return article.copy(
            archived = true,
            content = "",
            aiTranslation = "",
        )
    }

    private fun queryStaleArticles(cutoffTime: Long, minRelevanceScore: Float): List<Article> {
        return db.articleBox.query()
            .equal(Article_.archived, false)
            .equal(Article_.starred, false)
            .less(Article_.fetchedAt, cutoffTime)
            .less(Article_.aiRelevanceScore, minRelevanceScore.toDouble())
            .build()
            .use { it.find() }
            .filter { it.readAt == 0L }
    }

    private fun countActiveArticles(): Long {
        return db.articleBox.query()
            .equal(Article_.archived, false)
            .build()
            .use { it.count() }
    }

    companion object {
        internal const val DEFAULT_MAX_AGE_DAYS = 30
        internal const val DEFAULT_MIN_RELEVANCE = 0.3f
        internal const val DEFAULT_MAX_ARTICLES = 5000
        private const val EMERGENCY_SCAN_LIMIT = 2000
        private const val MILLIS_PER_DAY = 86_400_000L
    }
}
