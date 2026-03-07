package com.lumen.research.analyzer

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.memory.EmbeddingClient
import com.lumen.core.memory.LlmCall
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ArticleAnalyzer(
    private val db: LumenDatabase,
    private val llmCall: LlmCall,
    private val embeddingClient: EmbeddingClient,
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun analyze(article: Article): Article {
        val embeddingText = listOf(article.title, article.summary).filter { it.isNotBlank() }.joinToString(" ")
        val embedding = embeddingClient.embed(embeddingText)

        val (aiSummary, keywords) = try {
            val response = llmCall.execute(SYSTEM_PROMPT, buildUserPrompt(article))
            parseAnalysisResponse(response)
        } catch (_: Exception) {
            article.summary to ""
        }

        val updated = article.copy(
            aiSummary = aiSummary,
            keywords = keywords,
            embedding = embedding,
        )
        db.articleBox.put(updated)
        return db.articleBox.get(article.id)
    }

    suspend fun analyzeBatch(articles: List<Article>): List<Article> {
        val unanalyzed = articles.filter { it.aiSummary.isBlank() }
        return unanalyzed.map { analyze(it) }
    }

    internal fun buildUserPrompt(article: Article): String {
        val parts = mutableListOf<String>()
        parts.add("Title: ${article.title}")
        if (article.summary.isNotBlank()) parts.add("Summary: ${article.summary}")
        if (article.content.isNotBlank()) parts.add("Content: ${article.content}")
        return parts.joinToString("\n")
    }

    internal fun parseAnalysisResponse(response: String): Pair<String, String> {
        val jsonText = extractJsonObject(response)
        return try {
            val result = json.decodeFromString<AnalysisResult>(jsonText)
            result.summary to result.keywords.joinToString(",")
        } catch (_: Exception) {
            "" to ""
        }
    }

    private fun extractJsonObject(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) return "{}"
        return text.substring(start, end + 1)
    }

    @Serializable
    internal data class AnalysisResult(
        val summary: String = "",
        val keywords: List<String> = emptyList(),
    )

    companion object {
        internal const val SYSTEM_PROMPT = """You are a research article analyst. Analyze the given article and extract a concise summary and relevant keywords.

## Rules

1. Write a 2-3 sentence summary capturing the key contribution or finding.
2. Extract 3-7 keywords that represent the main topics.
3. Use technical terms appropriate for academic research.

## Output Format

Return a JSON object only, with no other text:

{"summary": "2-3 sentence summary", "keywords": ["keyword1", "keyword2", "keyword3"]}"""
    }
}
