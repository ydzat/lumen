package com.lumen.research.collector

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.Article_
import com.lumen.core.database.entities.Digest
import io.objectbox.query.QueryBuilder.StringOrder
import com.lumen.core.memory.EmbeddingClient
import com.lumen.core.util.formatEpochDate
import com.lumen.research.ProjectManager
import com.lumen.research.analyzer.ArticleAnalyzer
import com.lumen.research.analyzer.RelevanceScorer
import com.lumen.research.archiver.ArticleArchiver
import com.lumen.research.digest.DigestGenerator
import com.lumen.research.parseCsvSet
import com.lumen.research.spark.SparkEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class CollectorManager(
    private val articleAnalyzer: ArticleAnalyzer,
    private val relevanceScorer: RelevanceScorer,
    private val digestGenerator: DigestGenerator,
    private val dataSources: List<DataSource> = emptyList(),
    private val sourceManager: SourceManager? = null,
    private val deduplicator: Deduplicator? = null,
    private val embeddingClient: EmbeddingClient? = null,
    private val db: LumenDatabase? = null,
    private val projectManager: ProjectManager? = null,
    private val sparkEngine: SparkEngine? = null,
    private val articleArchiver: ArticleArchiver? = null,
) {

    suspend fun runPipeline(
        analysisBudget: Int = 10,
        progress: PipelineProgress? = null,
    ): PipelineResult {
        // 0. Generate spark keywords (cross-project discovery)
        val sparkKeywords = if (sparkEngine != null && projectManager != null) {
            val activeProjects = projectManager.listAll().filter { it.isActive }
            try {
                sparkEngine.generateSearchKeywords(activeProjects)
            } catch (_: Exception) {
                emptySet()
            }
        } else {
            emptySet()
        }

        // 1. Fetch
        progress?.onProgress(PipelineStage.FETCHING, 0, 1)
        val (fetchedArticles, fetchErrors) = if (dataSources.isNotEmpty() && sourceManager != null) {
            fetchViaDataSources(buildFetchContext(sparkKeywords = sparkKeywords))
        } else {
            emptyList<Article>() to emptyList<String>()
        }

        // 2. Dedup
        val afterDedup: List<Article>
        val duplicatesRemoved: Int
        if (deduplicator != null && fetchedArticles.isNotEmpty()) {
            progress?.onProgress(PipelineStage.DEDUPLICATING, 0, fetchedArticles.size)
            val dedupResult = deduplicator.deduplicate(fetchedArticles)
            afterDedup = dedupResult.unique
            duplicatesRemoved = dedupResult.duplicatesRemoved
        } else {
            afterDedup = fetchedArticles
            duplicatesRemoved = 0
        }

        // 3. Analyze (respects budget)
        progress?.onProgress(PipelineStage.ANALYZING, 0, afterDedup.size.coerceAtMost(analysisBudget))
        val analyzed = if (afterDedup.isNotEmpty()) {
            articleAnalyzer.analyzeBatch(afterDedup.take(analysisBudget))
        } else {
            emptyList()
        }

        // 4. Score
        progress?.onProgress(PipelineStage.SCORING, 0, analyzed.size)
        val scored = if (analyzed.isNotEmpty()) {
            relevanceScorer.scoreBatch(analyzed)
        } else {
            emptyList()
        }

        // 5. Digest
        progress?.onProgress(PipelineStage.DIGESTING, 0, 1)
        val today = formatEpochDate(System.currentTimeMillis())
        val digest = try {
            digestGenerator.generate(today)
        } catch (_: Exception) {
            null
        }

        // 6. Emergency archive (if storage exceeds limit)
        val archivedCount = if (articleArchiver != null &&
            articleArchiver.needsEmergencyArchive(ArticleArchiver.DEFAULT_MAX_ARTICLES)
        ) {
            try {
                articleArchiver.emergencyArchive(ArticleArchiver.DEFAULT_MAX_ARTICLES).archived
            } catch (_: Exception) {
                0
            }
        } else {
            0
        }

        return PipelineResult(
            fetched = fetchedArticles.size,
            analyzed = analyzed.size,
            scored = scored.size,
            digest = digest,
            scoredArticles = scored,
            fetchErrors = fetchErrors,
            duplicatesRemoved = duplicatesRemoved,
            sparkKeywords = sparkKeywords,
            archivedCount = archivedCount,
        )
    }

    suspend fun runNow(): PipelineResult = runPipeline()

    suspend fun fetchOnly(progress: PipelineProgress? = null): List<Article> {
        progress?.onProgress(PipelineStage.FETCHING, 0, 1)
        val (articles, _) = if (dataSources.isNotEmpty() && sourceManager != null) {
            fetchViaDataSources(buildFetchContext())
        } else {
            emptyList<Article>() to emptyList<String>()
        }
        return articles
    }

    suspend fun processUnembedded(): Int {
        if (db == null || embeddingClient == null) return 0
        val unembedded = db.articleBox.query()
            .equal(Article_.analysisStatus, "", StringOrder.CASE_SENSITIVE)
            .build()
            .use { it.find() }
            .filter { it.embedding.isEmpty() }

        var count = 0
        for (article in unembedded) {
            val text = listOf(article.title, article.summary).filter { it.isNotBlank() }.joinToString(" ")
            val embedding = embeddingClient.embed(text)
            db.articleBox.put(article.copy(embedding = embedding, analysisStatus = "embedded"))
            count++
        }
        return count
    }

    suspend fun analyzeTop(limit: Int = 10): Int {
        if (db == null) return 0
        val topUnanalyzed = db.articleBox.all
            .filter { it.aiSummary.isBlank() && it.embedding.isNotEmpty() }
            .sortedByDescending { it.aiRelevanceScore }
            .take(limit)

        val analyzed = articleAnalyzer.analyzeBatch(topUnanalyzed)
        return analyzed.size
    }

    suspend fun analyzeSingle(articleId: Long): Article? {
        if (db == null) return null
        val article = db.articleBox.get(articleId) ?: return null
        return articleAnalyzer.analyze(article)
    }

    internal fun buildFetchContext(
        budget: Int = 100,
        sparkKeywords: Set<String> = emptySet(),
    ): FetchContext {
        val allProjects = projectManager?.listAll() ?: emptyList()
        val activeProjects = allProjects.filter { it.isActive }
        val keywords = activeProjects.flatMap { parseCsvSet(it.keywords) }.toSet()
        return FetchContext(
            activeProjects = activeProjects,
            keywords = keywords,
            categories = emptySet(),
            remainingBudget = budget,
            sparkKeywords = sparkKeywords,
        )
    }

    private suspend fun fetchViaDataSources(context: FetchContext): Pair<List<Article>, List<String>> {
        val allSources = sourceManager!!.listEnabled()
        val sourcesByType = allSources.groupBy { SourceType.fromString(it.type) }

        val results = coroutineScope {
            sourcesByType.map { (type, sources) ->
                async {
                    try {
                        val ds = dataSources.find { it.type == type }
                        if (ds != null) {
                            ds.fetch(sources, context)
                        } else {
                            DataFetchResult(emptyList(), listOf("No handler for source type: $type"), type)
                        }
                    } catch (e: Exception) {
                        DataFetchResult(
                            emptyList(),
                            listOf("${type.name}: ${e.message ?: e::class.simpleName}"),
                            type,
                        )
                    }
                }
            }.awaitAll()
        }

        return results.flatMap { it.articles } to results.flatMap { it.errors }
    }
}

data class PipelineResult(
    val fetched: Int,
    val analyzed: Int,
    val scored: Int,
    val digest: Digest?,
    val scoredArticles: List<Article> = emptyList(),
    val fetchErrors: List<String> = emptyList(),
    val duplicatesRemoved: Int = 0,
    val sparkKeywords: Set<String> = emptySet(),
    val archivedCount: Int = 0,
)
