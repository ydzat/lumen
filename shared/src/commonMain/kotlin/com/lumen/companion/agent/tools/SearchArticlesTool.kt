package com.lumen.companion.agent.tools

import ai.koog.agents.core.tools.SimpleTool
import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article_
import com.lumen.core.memory.EmbeddingClient
import com.lumen.research.parseCsvSet
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Serializable
data class SearchArticlesArgs(
    val query: String,
    val projectId: Long = 0,
    val daysBack: Int = 0,
    val limit: Int = 5,
)

class SearchArticlesTool(
    private val db: LumenDatabase,
    private val embeddingClient: EmbeddingClient,
    private val defaultProjectId: Long = 0,
) : SimpleTool<SearchArticlesArgs>(
    SearchArticlesArgs.serializer(),
    "search_articles",
    "Search research articles by semantic similarity. Supports projectId, daysBack (date filter), and limit.",
) {
    override suspend fun execute(args: SearchArticlesArgs): String {
        val effectiveProjectId = args.projectId.takeIf { it > 0 } ?: defaultProjectId
        val needsFiltering = effectiveProjectId > 0 || args.daysBack > 0
        val embedding = embeddingClient.embed(args.query)

        val searchLimit = if (needsFiltering) args.limit * OVER_FETCH_FACTOR else args.limit
        val allArticles = db.articleBox.query()
            .nearestNeighbors(Article_.embedding, embedding, searchLimit)
            .build()
            .use { it.find() }

        val cutoffMs = if (args.daysBack > 0) {
            System.currentTimeMillis() - args.daysBack.toLong() * MS_PER_DAY
        } else {
            0L
        }

        val articles = allArticles
            .let { list ->
                if (effectiveProjectId > 0) {
                    list.filter { effectiveProjectId.toString() in parseCsvSet(it.projectIds) }
                } else {
                    list
                }
            }
            .let { list ->
                if (cutoffMs > 0) {
                    list.filter { it.publishedAt >= cutoffMs || it.fetchedAt >= cutoffMs }
                } else {
                    list
                }
            }
            .take(args.limit)

        if (articles.isEmpty()) return "No articles found."

        return articles.joinToString("\n\n") { article ->
            buildString {
                append("- **${article.title}**")
                if (article.aiSummary.isNotBlank()) {
                    append("\n  Summary: ${article.aiSummary}")
                }
                if (article.keywords.isNotBlank()) {
                    append("\n  Keywords: ${article.keywords}")
                }
                val dateMs = if (article.publishedAt > 0) article.publishedAt else article.fetchedAt
                if (dateMs > 0) {
                    append("\n  Date: ${formatEpoch(dateMs)}")
                }
                if (article.aiRelevanceScore > 0f) {
                    append("\n  Relevance: ${"%.2f".format(article.aiRelevanceScore)}")
                }
                if (article.url.isNotBlank()) {
                    append("\n  URL: ${article.url}")
                }
            }
        }
    }

    companion object {
        private const val OVER_FETCH_FACTOR = 3
        private const val MS_PER_DAY = 86_400_000L
        private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        private fun formatEpoch(epochMs: Long): String =
            Instant.ofEpochMilli(epochMs).atZone(ZoneOffset.UTC).format(DATE_FMT)
    }
}
