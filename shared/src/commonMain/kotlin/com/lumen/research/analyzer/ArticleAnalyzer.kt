package com.lumen.research.analyzer

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.memory.EmbeddingClient
import com.lumen.core.memory.LlmCall
import com.lumen.core.util.extractJsonObject
import com.lumen.research.collector.AnalysisStatus
import com.lumen.ui.stripHtml
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ArticleAnalyzer(
    private val db: LumenDatabase,
    private val llmCall: LlmCall,
    private val embeddingClient: EmbeddingClient,
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun analyze(article: Article): Article {
        val embedded = embedOnly(article)
        return analyzeWithLlm(embedded)
    }

    suspend fun embedOnly(article: Article): Article {
        return embedBatchOnly(listOf(article)).first()
    }

    suspend fun embedBatchOnly(articles: List<Article>): List<Article> {
        if (articles.isEmpty()) return emptyList()

        val texts = articles.map { article ->
            val contentPreview = article.content.take(CONTENT_EMBED_LIMIT)
            listOf(article.title, article.summary, contentPreview)
                .filter { it.isNotBlank() }.joinToString(" ")
        }
        val embeddings = embeddingClient.embedBatch(texts)

        return articles.zip(embeddings).map { (article, embedding) ->
            val updated = article.copy(
                embedding = embedding,
                analysisStatus = AnalysisStatus.EMBEDDED,
            )
            val id = db.articleBox.put(updated)
            db.articleBox.get(id)
        }
    }

    suspend fun analyzeWithLlm(article: Article): Article {
        val result = try {
            val response = llmCall.execute(SYSTEM_PROMPT, buildUserPrompt(article))
            parseAnalysisResponse(response)
        } catch (_: Exception) {
            AnalysisResult(summary = article.summary)
        }
        val updated = article.copy(
            aiSummary = result.summary,
            keywords = result.keywords.joinToString(","),
            analysisStatus = AnalysisStatus.ANALYZED,
        )
        val id = db.articleBox.put(updated)
        return db.articleBox.get(id)
    }

    suspend fun analyzeBatch(articles: List<Article>): List<Article> {
        val unanalyzed = articles.filter { it.aiSummary.isBlank() }
        return unanalyzed.map { analyze(it) }
    }

    internal fun buildUserPrompt(article: Article): String {
        val parts = mutableListOf<String>()
        parts.add("Title: ${article.title}")
        if (article.summary.isNotBlank()) parts.add("Summary: ${article.summary}")
        // Send truncated plain text of content (strip HTML, limit length) to avoid
        // wasting tokens on full HTML and images. The full content is rendered directly.
        if (article.content.isNotBlank()) {
            val plainText = stripHtml(article.content).take(CONTENT_LLM_LIMIT)
            parts.add("Content excerpt: $plainText")
        }
        return parts.joinToString("\n")
    }

    internal fun parseAnalysisResponse(response: String): AnalysisResult {
        val jsonText = extractJsonObject(response)
        return try {
            json.decodeFromString<AnalysisResult>(jsonText)
        } catch (_: Exception) {
            AnalysisResult()
        }
    }

    @Serializable
    internal data class AnalysisResult(
        val summary: String = "",
        val keywords: List<String> = emptyList(),
    )

    companion object {
        internal const val CONTENT_EMBED_LIMIT = 1000
        internal const val CONTENT_LLM_LIMIT = 3000
        internal const val SYSTEM_PROMPT = """You are a research article analyst. Analyze the given article and produce a concise summary and relevant keywords.

## Rules

1. Write a 2-3 sentence summary capturing the key contribution or finding.
2. Extract 3-7 keywords that represent the main topics.

## Output Format

Return a JSON object only, with no other text:

{"summary": "2-3 sentence summary", "keywords": ["k1", "k2"]}"""
    }
}
