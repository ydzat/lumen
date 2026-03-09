package com.lumen.research.analyzer

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.ResearchProject
import com.lumen.core.memory.MemoryManager
import com.lumen.core.memory.cosineSimilarity
import com.lumen.research.parseCsvSet

class RelevanceScorer(
    private val db: LumenDatabase,
    private val memoryManager: MemoryManager?,
) {

    suspend fun score(article: Article): Float {
        if (article.embedding.isEmpty()) return 0f

        val allProjects = db.researchProjectBox.all
            .filter { it.embedding.isNotEmpty() }
        val hasProject = allProjects.isNotEmpty()
        val hasMemory = memoryManager != null

        if (!hasProject && !hasMemory) return 0f

        val projectScore = if (hasProject) {
            allProjects.maxOf { project ->
                cosineSimilarity(article.embedding, project.embedding).coerceIn(0f, 1f)
            }
        } else 0f

        val memoryScore = if (hasMemory) {
            computeMemoryScore(article)
        } else 0f

        val keywordBoost = computeKeywordBoost(article, allProjects)

        val score = when {
            hasProject && hasMemory -> projectScore * 0.6f + memoryScore * 0.3f + keywordBoost * 0.1f
            hasProject -> projectScore * 0.85f + keywordBoost * 0.15f
            else -> memoryScore * 0.85f + keywordBoost * 0.15f
        }

        return score.coerceIn(0f, 1f)
    }

    fun assignToProjects(article: Article): Set<Long> {
        if (article.embedding.isEmpty()) return emptySet()
        val allProjects = db.researchProjectBox.all
            .filter { it.embedding.isNotEmpty() }
        if (allProjects.isEmpty()) return emptySet()

        val similarities = allProjects.map { project ->
            project.id to cosineSimilarity(article.embedding, project.embedding).coerceIn(0f, 1f)
        }
        val maxSim = similarities.maxOf { it.second }
        if (maxSim < MIN_ASSIGN_THRESHOLD) return emptySet()

        // Only assign to projects within RELATIVE_FACTOR of the best match
        val relativeThreshold = maxSim * RELATIVE_FACTOR
        return similarities
            .filter { it.second >= relativeThreshold && it.second >= MIN_ASSIGN_THRESHOLD }
            .map { it.first }
            .toSet()
    }

    suspend fun scoreAndPersist(article: Article): Article {
        val relevanceScore = score(article)
        val updated = article.copy(aiRelevanceScore = relevanceScore)
        val id = db.articleBox.put(updated)
        return db.articleBox.get(id)
    }

    suspend fun scoreBatch(articles: List<Article>): List<Article> {
        return articles.map { scoreAndPersist(it) }
    }

    private suspend fun computeMemoryScore(article: Article): Float {
        val memories = try {
            memoryManager!!.recall(article.title, limit = 3)
        } catch (_: Exception) {
            return 0f
        }
        if (memories.isEmpty()) return 0f

        val similarities = memories
            .filter { it.embedding.isNotEmpty() }
            .map { cosineSimilarity(article.embedding, it.embedding).coerceIn(0f, 1f) }

        return if (similarities.isEmpty()) 0f else similarities.average().toFloat()
    }

    private fun computeKeywordBoost(article: Article, projects: List<ResearchProject>): Float {
        val articleKeywords = parseCsvSet(article.keywords)
        if (articleKeywords.isEmpty()) return 0f

        val referenceKeywords = mutableSetOf<String>()
        for (project in projects) {
            referenceKeywords.addAll(parseCsvSet(project.keywords))
        }

        if (referenceKeywords.isEmpty()) return 0f

        val overlap = articleKeywords.count { kw ->
            referenceKeywords.any { ref -> ref.equals(kw, ignoreCase = true) }
        }
        return (overlap.toFloat() / articleKeywords.size).coerceIn(0f, 1f)
    }

    companion object {
        const val MIN_ASSIGN_THRESHOLD = 0.3f
        const val RELATIVE_FACTOR = 0.85f
    }
}
