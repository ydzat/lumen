package com.lumen.companion.agent.tools

import ai.koog.agents.core.tools.SimpleTool
import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article_
import com.lumen.core.memory.EmbeddingClient
import com.lumen.research.parseCsvSet
import kotlinx.serialization.Serializable

@Serializable
data class SearchArticlesArgs(
    val query: String,
    val projectId: Long = 0,
    val limit: Int = 5,
)

class SearchArticlesTool(
    private val db: LumenDatabase,
    private val embeddingClient: EmbeddingClient,
    private val defaultProjectId: Long = 0,
) : SimpleTool<SearchArticlesArgs>(
    SearchArticlesArgs.serializer(),
    "search_articles",
    "Search research articles by semantic similarity to a query. Optionally scope to a project.",
) {
    override suspend fun execute(args: SearchArticlesArgs): String {
        val effectiveProjectId = args.projectId.takeIf { it > 0 } ?: defaultProjectId
        val embedding = embeddingClient.embed(args.query)

        val searchLimit = if (effectiveProjectId > 0) args.limit * OVER_FETCH_FACTOR else args.limit
        val allArticles = db.articleBox.query()
            .nearestNeighbors(Article_.embedding, embedding, searchLimit)
            .build()
            .use { it.find() }

        val articles = if (effectiveProjectId > 0) {
            allArticles.filter { effectiveProjectId.toString() in parseCsvSet(it.projectIds) }.take(args.limit)
        } else {
            allArticles
        }

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
                if (article.url.isNotBlank()) {
                    append("\n  URL: ${article.url}")
                }
            }
        }
    }

    companion object {
        private const val OVER_FETCH_FACTOR = 3
    }
}
