package com.lumen.research.digest

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.Article_
import com.lumen.core.database.entities.Digest
import com.lumen.core.database.entities.Digest_
import io.objectbox.query.QueryBuilder
import com.lumen.core.memory.LlmCall
import com.lumen.core.util.extractJsonObject
import com.lumen.core.memory.MemoryManager
import com.lumen.core.util.dateToEpochRange
import com.lumen.research.parseCsvSet
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DigestGenerator(
    private val db: LumenDatabase,
    private val llmCall: LlmCall,
    private val memoryManager: MemoryManager?,
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generate(date: String, projectId: Long = 0): Digest {
        val existing = findExisting(date, projectId)
        if (existing != null) return existing

        val articles = collectArticles(date, projectId)
        val sourceBreakdown = buildSourceBreakdown(articles)

        val (title, content) = if (articles.isEmpty()) {
            "No articles for $date" to "No analyzed articles were found for this date."
        } else {
            generateDigestContent(articles, date)
        }

        val digest = Digest(
            date = date,
            title = title,
            content = content,
            sourceBreakdown = json.encodeToString(sourceBreakdown),
            projectId = projectId,
            createdAt = System.currentTimeMillis(),
        )
        val id = db.digestBox.put(digest)

        if (articles.isNotEmpty() && memoryManager != null) {
            storeToMemory(title, content, date)
        }

        return db.digestBox.get(id)
    }

    private fun findExisting(date: String, projectId: Long): Digest? {
        return db.digestBox.query()
            .equal(Digest_.date, date, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .equal(Digest_.projectId, projectId)
            .build()
            .use { it.findFirst() }
    }

    internal fun collectArticles(date: String, projectId: Long): List<Article> {
        val (startOfDay, endOfDay) = dateToEpochRange(date)
        val articles = db.articleBox.query()
            .between(Article_.fetchedAt, startOfDay, endOfDay - 1)
            .build()
            .use { it.find() }
        return articles
            .filter { it.aiSummary.isNotBlank() }
            .filter { projectId == 0L || projectId.toString() in parseCsvSet(it.projectIds) }
            .sortedByDescending { it.aiRelevanceScore }
    }

    internal fun buildSourceBreakdown(articles: List<Article>): List<SourceBreakdownEntry> {
        val sourceNames = db.sourceBox.all.associate { it.id to it.name }
        return articles
            .groupBy { it.sourceId }
            .map { (sourceId, group) ->
                SourceBreakdownEntry(
                    source = sourceNames[sourceId] ?: "Unknown",
                    count = group.size,
                    topArticle = group.maxByOrNull { it.aiRelevanceScore }?.title ?: "",
                )
            }
            .sortedByDescending { it.count }
    }

    private suspend fun generateDigestContent(articles: List<Article>, date: String): Pair<String, String> {
        val topArticles = articles.take(MAX_ARTICLES_FOR_PROMPT)
        val userPrompt = buildUserPrompt(topArticles, date)

        return try {
            val response = llmCall.execute(SYSTEM_PROMPT, userPrompt)
            val parsed = parseDigestResponse(response)
            if (parsed.first.isBlank()) buildFallbackDigest(topArticles, date) else parsed
        } catch (_: Exception) {
            buildFallbackDigest(topArticles, date)
        }
    }

    internal fun buildUserPrompt(articles: List<Article>, date: String): String {
        val sb = StringBuilder()
        sb.appendLine("Date: $date")
        sb.appendLine("Total articles: ${articles.size}")
        sb.appendLine()
        for ((i, article) in articles.withIndex()) {
            sb.appendLine("${i + 1}. [Score: ${"%.2f".format(article.aiRelevanceScore)}] ${article.title}")
            if (article.aiSummary.isNotBlank()) {
                sb.appendLine("   Summary: ${article.aiSummary}")
            }
            if (article.keywords.isNotBlank()) {
                sb.appendLine("   Keywords: ${article.keywords}")
            }
        }
        return sb.toString()
    }

    internal fun parseDigestResponse(response: String): Pair<String, String> {
        val jsonText = extractJsonObject(response)
        return try {
            val result = json.decodeFromString<DigestResponse>(jsonText)
            val content = buildString {
                appendLine(result.highlights)
                if (result.trends.isNotBlank()) {
                    appendLine()
                    appendLine(result.trends)
                }
            }.trim()
            result.title to content
        } catch (_: Exception) {
            "" to ""
        }
    }

    private fun buildFallbackDigest(articles: List<Article>, date: String): Pair<String, String> {
        val title = "Research Digest — $date"
        val content = buildString {
            appendLine("Top articles by relevance:")
            appendLine()
            for ((i, article) in articles.take(10).withIndex()) {
                appendLine("${i + 1}. ${article.title}")
                if (article.aiSummary.isNotBlank()) {
                    appendLine("   ${article.aiSummary}")
                }
            }
        }.trim()
        return title to content
    }

    private suspend fun storeToMemory(title: String, content: String, date: String) {
        try {
            val memoryContent = "Research digest for $date: $title. $content"
            memoryManager!!.store(
                content = memoryContent.take(MAX_MEMORY_CONTENT_LENGTH),
                category = "research_digest",
                source = "digest",
            )
        } catch (_: Exception) {
            // Memory storage is best-effort
        }
    }

    @Serializable
    internal data class DigestResponse(
        val title: String = "",
        val highlights: String = "",
        val trends: String = "",
    )

    @Serializable
    internal data class SourceBreakdownEntry(
        val source: String = "",
        val count: Int = 0,
        val topArticle: String = "",
    )

    companion object {
        internal const val MAX_ARTICLES_FOR_PROMPT = 20
        internal const val MAX_MEMORY_CONTENT_LENGTH = 1000

        internal const val SYSTEM_PROMPT = """You are a research digest generator. Summarize the day's research articles into a concise daily briefing.

## Rules

1. Write a compelling title for the digest.
2. Write highlights summarizing the most important findings and themes (3-5 paragraphs).
3. Identify emerging trends or patterns across articles (1-2 paragraphs).
4. Focus on substance, not article counts or metadata.

## Output Format

Return a JSON object only, with no other text:

{"title": "Digest title", "highlights": "Multi-paragraph highlights text", "trends": "Trend analysis text"}"""
    }
}
