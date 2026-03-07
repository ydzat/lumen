package com.lumen.research.collector

import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.Digest
import com.lumen.core.util.formatEpochDate
import com.lumen.research.analyzer.ArticleAnalyzer
import com.lumen.research.analyzer.RelevanceScorer
import com.lumen.research.digest.DigestGenerator

class CollectorManager(
    private val rssCollector: RssCollector,
    private val articleAnalyzer: ArticleAnalyzer,
    private val relevanceScorer: RelevanceScorer,
    private val digestGenerator: DigestGenerator,
) {

    suspend fun runPipeline(): PipelineResult {
        val newArticles = rssCollector.fetchAll()

        val analyzed = if (newArticles.isNotEmpty()) {
            articleAnalyzer.analyzeBatch(newArticles)
        } else {
            emptyList()
        }

        val scored = if (analyzed.isNotEmpty()) {
            relevanceScorer.scoreBatch(analyzed)
        } else {
            emptyList()
        }

        val today = formatEpochDate(System.currentTimeMillis())
        val digest = try {
            digestGenerator.generate(today)
        } catch (_: Exception) {
            null
        }

        return PipelineResult(
            fetched = newArticles.size,
            analyzed = analyzed.size,
            scored = scored.size,
            digest = digest,
            scoredArticles = scored,
        )
    }

    suspend fun runNow(): PipelineResult = runPipeline()
}

data class PipelineResult(
    val fetched: Int,
    val analyzed: Int,
    val scored: Int,
    val digest: Digest?,
    val scoredArticles: List<Article> = emptyList(),
)
