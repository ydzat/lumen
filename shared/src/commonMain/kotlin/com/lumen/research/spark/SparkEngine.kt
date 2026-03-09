package com.lumen.research.spark

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.Article_
import com.lumen.core.database.entities.ResearchProject
import com.lumen.core.memory.EmbeddingClient
import com.lumen.core.memory.LlmCall
import com.lumen.core.util.extractJsonObject
import com.lumen.research.languageDisplayName
import com.lumen.research.parseCsvSet
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class Spark(
    val title: String,
    val description: String,
    val relatedKeywords: List<String>,
    val sourceProjectIds: List<Long>,
)

class SparkEngine(
    private val llmCall: LlmCall,
    private val db: LumenDatabase,
    private val embeddingClient: EmbeddingClient? = null,
    private val language: String = "en",
) {

    suspend fun generateSearchKeywords(projects: List<ResearchProject>): Set<String> {
        if (projects.size < MIN_PROJECTS) return emptySet()

        val (systemPrompt, userPrompt) = buildKeywordPrompt(projects)
        return try {
            val response = llmCall.execute(systemPrompt, userPrompt)
            parseKeywordsResponse(response)
        } catch (_: Exception) {
            emptySet()
        }
    }

    suspend fun generateInsights(projects: List<ResearchProject>): List<Spark> {
        if (projects.size < MIN_PROJECTS) return emptyList()

        val keywords = generateSearchKeywords(projects)
        if (keywords.isEmpty()) return emptyList()

        val articles = searchArticlesByKeywords(keywords)
        if (articles.isEmpty()) return emptyList()

        val (systemPrompt, userPrompt) = buildInsightPrompt(projects, articles)
        return try {
            val response = llmCall.execute(systemPrompt, userPrompt)
            parseInsightsResponse(response)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun realtimeSpark(
        currentTopic: String,
        projects: List<ResearchProject>,
    ): Spark? {
        if (projects.size < MIN_PROJECTS) return null

        val (systemPrompt, userPrompt) = buildRealtimePrompt(currentTopic, projects)
        return try {
            val response = llmCall.execute(systemPrompt, userPrompt)
            parseRealtimeResponse(response)
        } catch (_: Exception) {
            null
        }
    }

    internal fun buildKeywordPrompt(projects: List<ResearchProject>): Pair<String, String> {
        val systemPrompt = """You generate cross-domain search keywords that bridge multiple research projects.
Output JSON: {"keywords": ["keyword1", "keyword2", ...]}
Rules:
- Generate keywords that connect concepts across different projects
- Max $MAX_KEYWORDS keywords
- Each keyword should be 1-4 words
- Focus on intersection points, not individual project topics
- Keywords should be in English (for search purposes)"""

        val projectSummaries = projects.joinToString("\n") { project ->
            val keywords = parseCsvSet(project.keywords).joinToString(", ")
            "- ${project.name}: $keywords"
        }
        val userPrompt = "Research projects:\n$projectSummaries"

        return systemPrompt to userPrompt
    }

    internal fun buildInsightPrompt(
        projects: List<ResearchProject>,
        articles: List<Article>,
    ): Pair<String, String> {
        val langName = languageDisplayName(language)
        val systemPrompt = """You find unexpected connections between research domains.
Output JSON: {"sparks": [{"title": "short title", "description": "1-2 sentence insight", "relatedKeywords": ["kw1", "kw2"], "sourceProjectIds": [1, 2]}]}
Rules:
- Each spark connects concepts from at least 2 different projects
- Be specific and actionable, not generic
- Max 3 sparks
- Write title and description in $langName. Keywords should remain in English."""

        val projectSection = projects.joinToString("\n") { project ->
            val keywords = parseCsvSet(project.keywords).joinToString(", ")
            "- [ID:${project.id}] ${project.name}: $keywords"
        }
        val articleSection = articles.joinToString("\n") { article ->
            val summary = article.aiSummary.ifBlank { article.summary }.take(ARTICLE_SUMMARY_MAX)
            "- ${article.title}: $summary"
        }
        val userPrompt = "Projects:\n$projectSection\n\nRecent articles:\n$articleSection"

        return systemPrompt to userPrompt
    }

    internal fun buildRealtimePrompt(
        topic: String,
        projects: List<ResearchProject>,
    ): Pair<String, String> {
        val langName = languageDisplayName(language)
        val systemPrompt = """Based on the current discussion topic, suggest one cross-project insight.
Output JSON: {"title": "short title", "description": "1-2 sentence insight", "relatedKeywords": ["kw1", "kw2"], "sourceProjectIds": [1, 2]}
Write title and description in $langName.
If no meaningful cross-project connection exists, output: {"title": "", "description": "", "relatedKeywords": [], "sourceProjectIds": []}"""

        val projectSection = projects.joinToString("\n") { project ->
            val keywords = parseCsvSet(project.keywords).joinToString(", ")
            "- [ID:${project.id}] ${project.name}: $keywords"
        }
        val userPrompt = "Current topic: $topic\n\nProjects:\n$projectSection"

        return systemPrompt to userPrompt
    }

    internal fun parseKeywordsResponse(response: String): Set<String> {
        return try {
            val jsonStr = extractJsonObject(response)
            val parsed = json.decodeFromString<KeywordsResponse>(jsonStr)
            parsed.keywords
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .take(MAX_KEYWORDS)
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    internal fun parseInsightsResponse(response: String): List<Spark> {
        return try {
            val jsonStr = extractJsonObject(response)
            val parsed = json.decodeFromString<SparksResponse>(jsonStr)
            parsed.sparks
                .filter { it.title.isNotBlank() && it.description.isNotBlank() }
                .map { Spark(it.title, it.description, it.relatedKeywords, it.sourceProjectIds) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    internal fun parseRealtimeResponse(response: String): Spark? {
        return try {
            val jsonStr = extractJsonObject(response)
            val parsed = json.decodeFromString<SparkDto>(jsonStr)
            if (parsed.title.isBlank() || parsed.description.isBlank()) return null
            Spark(parsed.title, parsed.description, parsed.relatedKeywords, parsed.sourceProjectIds)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun searchArticlesByKeywords(
        keywords: Set<String>,
        limit: Int = ARTICLE_SEARCH_LIMIT,
    ): List<Article> {
        if (embeddingClient == null) return searchArticlesByText(keywords, limit)

        val allResults = mutableSetOf<Long>()
        val articles = mutableListOf<Article>()

        for (keyword in keywords) {
            if (articles.size >= limit) break
            try {
                val embedding = embeddingClient.embed(keyword)
                val nearest = db.articleBox.query()
                    .nearestNeighbors(Article_.embedding, embedding, (limit / keywords.size).coerceAtLeast(3))
                    .build()
                    .use { it.find() }

                for (article in nearest) {
                    if (article.id !in allResults && articles.size < limit) {
                        allResults.add(article.id)
                        articles.add(article)
                    }
                }
            } catch (_: Exception) {
                // Skip failed embeddings
            }
        }

        return articles
    }

    private fun searchArticlesByText(keywords: Set<String>, limit: Int): List<Article> {
        val allArticles = db.articleBox.query()
            .notEqual(Article_.title, "", StringOrder.CASE_SENSITIVE)
            .build()
            .use { it.find(0, TEXT_SEARCH_SCAN_LIMIT.toLong()) }

        return allArticles
            .filter { article ->
                val text = "${article.title} ${article.keywords} ${article.summary}".lowercase()
                keywords.any { kw -> text.contains(kw.lowercase()) }
            }
            .take(limit)
    }

    companion object {
        internal const val MIN_PROJECTS = 2
        internal const val MAX_KEYWORDS = 10
        private const val ARTICLE_SEARCH_LIMIT = 10
        private const val TEXT_SEARCH_SCAN_LIMIT = 1000
        private const val ARTICLE_SUMMARY_MAX = 200

        private val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
internal data class KeywordsResponse(
    val keywords: List<String> = emptyList(),
)

@Serializable
internal data class SparksResponse(
    val sparks: List<SparkDto> = emptyList(),
)

@Serializable
internal data class SparkDto(
    val title: String = "",
    val description: String = "",
    val relatedKeywords: List<String> = emptyList(),
    val sourceProjectIds: List<Long> = emptyList(),
)
