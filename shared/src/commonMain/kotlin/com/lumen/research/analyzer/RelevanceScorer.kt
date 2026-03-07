package com.lumen.research.analyzer

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.ResearchProject
import com.lumen.core.database.entities.ResearchProject_
import com.lumen.core.memory.MemoryManager
import com.lumen.core.memory.cosineSimilarity
import com.lumen.research.parseCsvSet

class RelevanceScorer(
    private val db: LumenDatabase,
    private val memoryManager: MemoryManager?,
) {

    suspend fun score(article: Article): Float {
        if (article.embedding.isEmpty()) return 0f

        val activeProject = db.researchProjectBox.query()
            .equal(ResearchProject_.isActive, true)
            .build()
            .use { it.findFirst() }
        val hasProject = activeProject != null && activeProject.embedding.isNotEmpty()
        val hasMemory = memoryManager != null

        if (!hasProject && !hasMemory) return 0f

        val projectScore = if (hasProject) {
            cosineSimilarity(article.embedding, activeProject!!.embedding).coerceIn(0f, 1f)
        } else 0f

        val memoryScore = if (hasMemory) {
            computeMemoryScore(article)
        } else 0f

        val keywordBoost = computeKeywordBoost(article, activeProject)

        val score = when {
            hasProject && hasMemory -> projectScore * 0.6f + memoryScore * 0.3f + keywordBoost * 0.1f
            hasProject -> projectScore * 0.85f + keywordBoost * 0.15f
            else -> memoryScore * 0.85f + keywordBoost * 0.15f
        }

        return score.coerceIn(0f, 1f)
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

    private fun computeKeywordBoost(article: Article, activeProject: ResearchProject?): Float {
        val articleKeywords = parseCsvSet(article.keywords)
        if (articleKeywords.isEmpty()) return 0f

        val referenceKeywords = mutableSetOf<String>()
        if (activeProject != null) {
            referenceKeywords.addAll(parseCsvSet(activeProject.keywords))
        }

        if (referenceKeywords.isEmpty()) return 0f

        val overlap = articleKeywords.count { kw ->
            referenceKeywords.any { ref -> ref.equals(kw, ignoreCase = true) }
        }
        return (overlap.toFloat() / articleKeywords.size).coerceIn(0f, 1f)
    }

}
