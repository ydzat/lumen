package com.lumen.companion.agent.tools

import ai.koog.agents.core.tools.SimpleTool
import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article_
import com.lumen.core.memory.EmbeddingClient
import kotlinx.serialization.Serializable

@Serializable
data class SearchArticlesArgs(
    val query: String,
    val limit: Int = 5,
)

class SearchArticlesTool(
    private val db: LumenDatabase,
    private val embeddingClient: EmbeddingClient,
) : SimpleTool<SearchArticlesArgs>(
    SearchArticlesArgs.serializer(),
    "search_articles",
    "Search research articles by semantic similarity to a query",
) {
    override suspend fun execute(args: SearchArticlesArgs): String {
        val embedding = embeddingClient.embed(args.query)
        val articles = db.articleBox.query()
            .nearestNeighbors(Article_.embedding, embedding, args.limit)
            .build()
            .use { it.find() }

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
}
