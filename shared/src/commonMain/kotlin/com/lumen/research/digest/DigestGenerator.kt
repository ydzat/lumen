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
import com.lumen.research.collector.AnalysisStatus
import com.lumen.research.ProjectManager
import com.lumen.research.languageDisplayName
import com.lumen.research.parseCsvSet
import com.lumen.research.spark.Spark
import com.lumen.research.spark.SparkEngine
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DigestGenerator(
    private val db: LumenDatabase,
    private val llmCall: LlmCall,
    private val memoryManager: MemoryManager?,
    private val sparkEngine: SparkEngine? = null,
    private val projectManager: ProjectManager? = null,
    private val language: String = "en",
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generate(date: String, projectId: Long = 0): Digest {
        val existing = findExisting(date, projectId)
        if (existing != null) return existing

        val articles = collectArticles(date, projectId)
        val sourceBreakdown = buildSourceBreakdown(articles)

        if (articles.isEmpty()) {
            val digest = Digest(
                date = date,
                title = "No articles for $date",
                content = "No analyzed articles were found for this date.",
                sourceBreakdown = json.encodeToString(sourceBreakdown),
                projectId = projectId,
                createdAt = System.currentTimeMillis(),
            )
            val id = db.digestBox.put(digest)
            return db.digestBox.get(id)
        }

        // 1. Build category sections (project-based or auto-categorized)
        val rawSections = if (projectId == 0L) {
            val hasProjects = projectManager?.listAll()?.isNotEmpty() == true
            val projectSections = buildProjectSections(articles)
            val hasAssignedArticles = projectSections.any { it.projectId != 0L }
            if (hasProjects && hasAssignedArticles) projectSections
            else buildAutoCategorySections(articles)
        } else {
            emptyList()
        }

        // 2. Generate LLM analysis with per-section content
        val llmResponse = if (rawSections.isNotEmpty()) {
            generateDigestContent(rawSections, date, articles.size)
        } else {
            // Single-project or no sections: simpler generation
            val singleSection = listOf(
                ProjectSection(
                    projectName = "All",
                    articleCount = articles.size,
                    articles = articles.take(MAX_PROJECT_HIGHLIGHTS).map { article ->
                        ArticleReference(
                            articleId = article.id,
                            title = article.title,
                            url = article.url,
                            relevanceScore = article.aiRelevanceScore,
                            summarySnippet = (article.aiSummary.ifBlank { article.summary }).take(SNIPPET_MAX),
                        )
                    },
                )
            )
            generateDigestContent(singleSection, date, articles.size)
        }

        // 3. Merge LLM analysis into project sections
        val enrichedSections = mergeLlmAnalysis(rawSections, llmResponse.sections)

        // 4. Spark insights (from SparkEngine or extracted from LLM section insights)
        val sparks = buildSparkContent(enrichedSections)

        val digest = Digest(
            date = date,
            title = llmResponse.title,
            content = llmResponse.overview,
            sourceBreakdown = json.encodeToString(sourceBreakdown),
            projectId = projectId,
            createdAt = System.currentTimeMillis(),
            projectSections = if (enrichedSections.isNotEmpty()) json.encodeToString(enrichedSections) else "",
            sparks = if (sparks.isNotEmpty()) json.encodeToString(sparks) else "",
        )
        val id = db.digestBox.put(digest)

        if (memoryManager != null) {
            storeToMemory(llmResponse.title, llmResponse.overview, date)
        }

        return db.digestBox.get(id)
    }

    private fun mergeLlmAnalysis(
        sections: List<ProjectSection>,
        analyses: List<SectionAnalysis>,
    ): List<ProjectSection> {
        val analysisByCategory = analyses.associateBy { it.category.lowercase() }
        return sections.map { section ->
            val analysis = analysisByCategory[section.projectName.lowercase()]
            if (analysis != null) {
                section.copy(
                    highlights = analysis.highlights,
                    trends = analysis.trends,
                    insight = analysis.insight,
                )
            } else {
                section
            }
        }
    }

    private suspend fun buildSparkContent(sections: List<ProjectSection>): List<SparkSection> {
        // Try SparkEngine first (cross-project insights from dedicated engine)
        if (sparkEngine != null && projectManager != null) {
            try {
                val activeProjects = projectManager.listAll().filter { it.isActive }
                val insights = sparkEngine.generateInsights(activeProjects)
                if (insights.isNotEmpty()) {
                    return buildSparkSections(insights)
                }
            } catch (_: Exception) {
                // Fall through to LLM-derived insights
            }
        }

        // Fallback: use per-section insights from LLM analysis as spark-like content
        return sections
            .filter { it.insight.isNotBlank() }
            .map { section ->
                SparkSection(
                    title = section.projectName,
                    description = section.insight,
                )
            }
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
            .filter { it.analysisStatus == AnalysisStatus.ANALYZED }
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

    internal fun buildProjectSections(articles: List<Article>): List<ProjectSection> {
        val projectNames = projectManager?.listAll()?.associate { it.id to it.name } ?: emptyMap()
        val grouped = mutableMapOf<Long, MutableList<Article>>()

        for (article in articles) {
            val pids = parseCsvSet(article.projectIds).mapNotNull { it.toLongOrNull() }
            if (pids.isEmpty()) {
                grouped.getOrPut(0L) { mutableListOf() }.add(article)
            } else {
                for (pid in pids) {
                    grouped.getOrPut(pid) { mutableListOf() }.add(article)
                }
            }
        }

        return grouped.map { (pid, arts) ->
            val sortedArts = arts.sortedByDescending { it.aiRelevanceScore }
            val highlights = sortedArts.take(MAX_PROJECT_HIGHLIGHTS).joinToString("\n") { article ->
                val summary = article.aiSummary.ifBlank { article.summary }
                "- ${article.title}: ${summary.take(HIGHLIGHT_SUMMARY_MAX)}"
            }
            val articleRefs = sortedArts.take(MAX_PROJECT_HIGHLIGHTS).map { article ->
                ArticleReference(
                    articleId = article.id,
                    title = article.title,
                    url = article.url,
                    relevanceScore = article.aiRelevanceScore,
                    summarySnippet = (article.aiSummary.ifBlank { article.summary }).take(SNIPPET_MAX),
                )
            }
            ProjectSection(
                projectId = pid,
                projectName = projectNames[pid] ?: if (pid == 0L) "Unassigned" else "Project $pid",
                highlights = highlights,
                articleCount = arts.size,
                articles = articleRefs,
            )
        }.sortedByDescending { it.articleCount }
    }

    internal fun buildAutoCategorySections(articles: List<Article>): List<ProjectSection> {
        val keywordToArticles = mutableMapOf<String, MutableList<Article>>()
        for (article in articles) {
            val keywords = parseCsvSet(article.keywords)
            if (keywords.isEmpty()) {
                keywordToArticles.getOrPut("General") { mutableListOf() }.add(article)
            } else {
                for (kw in keywords) {
                    keywordToArticles.getOrPut(kw.trim()) { mutableListOf() }.add(article)
                }
            }
        }

        // Pick top categories by article count, merge small ones into "Other"
        val sorted = keywordToArticles.entries.sortedByDescending { it.value.size }
        val topCategories = sorted.take(MAX_AUTO_CATEGORIES)
        val assigned = mutableSetOf<Long>()
        val sections = mutableListOf<ProjectSection>()

        for ((keyword, arts) in topCategories) {
            val unique = arts.filter { it.id !in assigned }.sortedByDescending { it.aiRelevanceScore }
            if (unique.isEmpty()) continue
            unique.forEach { assigned.add(it.id) }
            val articleRefs = unique.take(MAX_PROJECT_HIGHLIGHTS).map { article ->
                ArticleReference(
                    articleId = article.id,
                    title = article.title,
                    url = article.url,
                    relevanceScore = article.aiRelevanceScore,
                    summarySnippet = (article.aiSummary.ifBlank { article.summary }).take(SNIPPET_MAX),
                )
            }
            sections.add(
                ProjectSection(
                    projectId = 0L,
                    projectName = keyword,
                    highlights = unique.take(MAX_PROJECT_HIGHLIGHTS).joinToString("\n") { a ->
                        "- ${a.title}: ${(a.aiSummary.ifBlank { a.summary }).take(HIGHLIGHT_SUMMARY_MAX)}"
                    },
                    articleCount = unique.size,
                    articles = articleRefs,
                )
            )
        }

        // Remaining unassigned articles
        val remaining = articles.filter { it.id !in assigned }
        if (remaining.isNotEmpty()) {
            val articleRefs = remaining.sortedByDescending { it.aiRelevanceScore }
                .take(MAX_PROJECT_HIGHLIGHTS).map { article ->
                    ArticleReference(
                        articleId = article.id,
                        title = article.title,
                        url = article.url,
                        relevanceScore = article.aiRelevanceScore,
                        summarySnippet = (article.aiSummary.ifBlank { article.summary }).take(SNIPPET_MAX),
                    )
                }
            sections.add(
                ProjectSection(
                    projectId = 0L,
                    projectName = "Other",
                    highlights = "",
                    articleCount = remaining.size,
                    articles = articleRefs,
                )
            )
        }

        return sections.sortedByDescending { it.articleCount }
    }

    internal fun buildSparkSections(sparks: List<Spark>): List<SparkSection> {
        return sparks.map { spark ->
            SparkSection(
                title = spark.title,
                description = spark.description,
                relatedKeywords = spark.relatedKeywords,
            )
        }
    }

    suspend fun regenerate(date: String, projectId: Long = 0): Digest {
        val existing = findExisting(date, projectId)
        if (existing != null) {
            db.digestBox.remove(existing.id)
        }
        return generate(date, projectId)
    }

    internal suspend fun generateDigestContent(
        sections: List<ProjectSection>,
        date: String,
        totalArticles: Int,
    ): DigestResponse {
        val categoryNames = sections.map { it.projectName }
        val userPrompt = buildUserPrompt(sections, date, totalArticles)

        return try {
            val response = llmCall.execute(buildSystemPrompt(categoryNames), userPrompt)
            val parsed = parseDigestResponse(response)
            if (parsed.title.isBlank()) buildFallbackResponse(sections, date) else parsed
        } catch (_: Exception) {
            buildFallbackResponse(sections, date)
        }
    }

    internal fun buildUserPrompt(
        sections: List<ProjectSection>,
        date: String,
        totalArticles: Int,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("Date: $date")
        sb.appendLine("Total articles: $totalArticles")
        sb.appendLine()
        for (section in sections) {
            sb.appendLine("## ${section.projectName} (${section.articleCount} articles)")
            for (ref in section.articles) {
                sb.appendLine("- [${"%.2f".format(ref.relevanceScore)}] ${ref.title}")
                if (ref.summarySnippet.isNotBlank()) {
                    sb.appendLine("  ${ref.summarySnippet}")
                }
            }
            sb.appendLine()
        }
        return sb.toString()
    }

    internal fun parseDigestResponse(response: String): DigestResponse {
        val jsonText = extractJsonObject(response)
        return try {
            json.decodeFromString<DigestResponse>(jsonText)
        } catch (_: Exception) {
            DigestResponse()
        }
    }

    private fun buildFallbackResponse(
        sections: List<ProjectSection>,
        date: String,
    ): DigestResponse {
        val overview = sections.joinToString("; ") { "${it.projectName}: ${it.articleCount} articles" }
        val sectionAnalyses = sections.map { section ->
            val topArticles = section.articles.take(3).joinToString("\n") { ref ->
                "- ${ref.title}"
            }
            SectionAnalysis(
                category = section.projectName,
                highlights = topArticles,
                trends = "",
                insight = "",
            )
        }
        return DigestResponse(
            title = "Research Digest — $date",
            overview = overview,
            sections = sectionAnalyses,
        )
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
        val overview: String = "",
        val sections: List<SectionAnalysis> = emptyList(),
    )

    @Serializable
    internal data class SectionAnalysis(
        val category: String = "",
        val highlights: String = "",
        val trends: String = "",
        val insight: String = "",
    )

    @Serializable
    data class SourceBreakdownEntry(
        val source: String = "",
        val count: Int = 0,
        val topArticle: String = "",
    )

    @Serializable
    data class ArticleReference(
        val articleId: Long = 0,
        val title: String = "",
        val url: String = "",
        val relevanceScore: Float = 0f,
        val summarySnippet: String = "",
    )

    @Serializable
    data class ProjectSection(
        val projectId: Long = 0,
        val projectName: String = "",
        val highlights: String = "",
        val trends: String = "",
        val insight: String = "",
        val articleCount: Int = 0,
        val articles: List<ArticleReference> = emptyList(),
    )

    @Serializable
    data class SparkSection(
        val title: String = "",
        val description: String = "",
        val relatedKeywords: List<String> = emptyList(),
    )

    internal fun buildSystemPrompt(categoryNames: List<String>): String {
        val langName = languageDisplayName(language)
        val sectionsDesc = categoryNames.joinToString(", ") { "\"$it\"" }
        return """You are a research digest generator. Produce a structured daily briefing.

## Rules

1. Write a compelling title for the digest.
2. Write a concise overview (2-3 sentences) that synthesizes the most important insights across ALL categories.
3. For EACH category ($sectionsDesc), write:
   - highlights: a numbered list of 2-3 key findings specific to that category
   - trends: 1-2 emerging patterns within that category
   - insight: one cross-cutting observation connecting this category to others (or a novel angle)
4. Focus on substance, not article counts or metadata.
5. Write all text content in $langName.

## Output Format

Return a JSON object only, with no other text:

{"title": "Digest title", "overview": "2-3 sentence executive summary", "sections": [{"category": "Category Name", "highlights": "1. Finding\n2. Finding", "trends": "1. Trend", "insight": "Cross-cutting observation"}]}"""
    }

    companion object {
        internal const val MAX_ARTICLES_FOR_PROMPT = 20
        internal const val MAX_MEMORY_CONTENT_LENGTH = 1000
        private const val MAX_PROJECT_HIGHLIGHTS = 5
        private const val HIGHLIGHT_SUMMARY_MAX = 200
        private const val SNIPPET_MAX = 100
        private const val MAX_AUTO_CATEGORIES = 5
    }
}
