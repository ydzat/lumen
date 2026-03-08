package com.lumen.research.collector

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.MyObjectBox
import com.lumen.core.database.entities.Source
import com.lumen.core.memory.EmbeddingClient
import com.lumen.core.memory.LlmCall
import com.lumen.research.analyzer.ArticleAnalyzer
import com.lumen.research.analyzer.RelevanceScorer
import com.lumen.research.digest.DigestGenerator
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CollectorManagerTest {

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File

    private val noopLlmCall = LlmCall { _, _ -> """{"summary": "test summary", "keywords": ["ai"]}""" }

    private val fakeEmbeddingClient = object : EmbeddingClient {
        override suspend fun embed(text: String): FloatArray = FloatArray(384) { 0.1f }
        override suspend fun embedBatch(texts: List<String>): List<FloatArray> = texts.map { embed(it) }
    }

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "objectbox-test-${System.nanoTime()}")
        tempDir.mkdirs()
        val store = MyObjectBox.builder().baseDirectory(tempDir).build()
        db = LumenDatabase(store)
    }

    @AfterTest
    fun teardown() {
        db.close()
        tempDir.deleteRecursively()
    }

    private fun createManager(): CollectorManager {
        val analyzer = ArticleAnalyzer(db, noopLlmCall, fakeEmbeddingClient)
        val scorer = RelevanceScorer(db, null)
        val digestGenerator = DigestGenerator(db, noopLlmCall, null)
        return CollectorManager(analyzer, scorer, digestGenerator)
    }

    private fun createManagerWithDataSources(
        dataSources: List<DataSource> = emptyList(),
    ): CollectorManager {
        val analyzer = ArticleAnalyzer(db, noopLlmCall, fakeEmbeddingClient)
        val scorer = RelevanceScorer(db, null)
        val digestGenerator = DigestGenerator(db, noopLlmCall, null)
        val sourceManager = SourceManager(db)
        val deduplicator = Deduplicator(db)
        return CollectorManager(
            articleAnalyzer = analyzer,
            relevanceScorer = scorer,
            digestGenerator = digestGenerator,
            dataSources = dataSources,
            sourceManager = sourceManager,
            deduplicator = deduplicator,
            embeddingClient = fakeEmbeddingClient,
            db = db,
        )
    }

    @Test
    fun runPipeline_withNoSources_returnsZeroCounts() = runBlocking {
        val manager = createManager()

        val result = manager.runPipeline()

        assertEquals(0, result.fetched)
        assertEquals(0, result.analyzed)
        assertEquals(0, result.scored)
    }

    @Test
    fun runPipeline_withSourceButNoNetwork_returnsZeroFetched() = runBlocking {
        db.sourceBox.put(
            Source(name = "Unreachable", url = "https://invalid.test.example/feed", type = "RSS")
        )
        val manager = createManager()

        val result = manager.runPipeline()

        assertEquals(0, result.fetched)
        assertEquals(0, result.analyzed)
        assertEquals(0, result.scored)
    }

    @Test
    fun runPipeline_digestGenerated_forToday() = runBlocking {
        val manager = createManager()

        val result = manager.runPipeline()

        assertNotNull(result.digest)
        assertEquals("No articles for ${result.digest!!.date}", result.digest!!.title)
    }

    @Test
    fun runNow_delegatesToRunPipeline() = runBlocking {
        val manager = createManager()

        val result = manager.runNow()

        assertEquals(0, result.fetched)
        assertEquals(0, result.analyzed)
        assertEquals(0, result.scored)
    }

    @Test
    fun runPipeline_withEmptyDataSources_returnsEmpty() = runBlocking {
        val manager = createManagerWithDataSources(emptyList())

        val result = manager.runPipeline()

        assertEquals(0, result.fetched)
        assertEquals(0, result.analyzed)
    }

    @Test
    fun runPipeline_withMockDataSource_dispatchesByType() = runBlocking {
        val fakeArticle = Article(
            title = "Fake arXiv Paper",
            url = "https://arxiv.org/abs/2401.00001",
            sourceType = "ARXIV_API",
        )
        val fakeDataSource = object : DataSource {
            override val type = SourceType.ARXIV_API
            override val displayName = "Fake arXiv"
            override suspend fun fetch(sources: List<Source>, context: FetchContext): DataFetchResult {
                return DataFetchResult(listOf(fakeArticle), emptyList(), SourceType.ARXIV_API)
            }
        }

        db.sourceBox.put(Source(name = "arXiv", url = "https://arxiv.org", type = "ARXIV_API"))
        val manager = createManagerWithDataSources(listOf(fakeDataSource))

        val result = manager.runPipeline(analysisBudget = 1)

        assertEquals(1, result.fetched)
    }

    @Test
    fun runPipeline_withDeduplicator_removesDuplicates() = runBlocking {
        val fakeDataSource = object : DataSource {
            override val type = SourceType.ARXIV_API
            override val displayName = "Fake"
            override suspend fun fetch(sources: List<Source>, context: FetchContext): DataFetchResult {
                return DataFetchResult(
                    listOf(
                        Article(title = "Paper A", url = "https://example.com/same"),
                        Article(title = "Paper B", url = "https://example.com/same"),
                    ),
                    emptyList(),
                    SourceType.ARXIV_API,
                )
            }
        }

        db.sourceBox.put(Source(name = "arXiv", url = "https://arxiv.org", type = "ARXIV_API"))
        val manager = createManagerWithDataSources(listOf(fakeDataSource))

        val result = manager.runPipeline(analysisBudget = 5)

        assertEquals(2, result.fetched)
        assertEquals(1, result.duplicatesRemoved)
    }

    @Test
    fun fetchOnly_returnsArticlesWithoutAnalysis() = runBlocking {
        val manager = createManager()

        val articles = manager.fetchOnly()

        assertTrue(articles.isEmpty())
    }

    @Test
    fun buildFetchContext_withNoProjects_returnsEmptyKeywords() {
        val manager = createManagerWithDataSources()

        val context = manager.buildFetchContext()

        assertTrue(context.activeProjects.isEmpty())
        assertTrue(context.keywords.isEmpty())
        assertEquals(100, context.remainingBudget)
    }

    @Test
    fun runPipeline_progressCallbackInvoked() = runBlocking {
        val manager = createManager()
        val stages = mutableListOf<PipelineStage>()

        manager.runPipeline(progress = PipelineProgress { stage, _, _ -> stages.add(stage) })

        assertTrue(stages.contains(PipelineStage.FETCHING))
        assertTrue(stages.contains(PipelineStage.ANALYZING))
        assertTrue(stages.contains(PipelineStage.SCORING))
        assertTrue(stages.contains(PipelineStage.DIGESTING))
    }
}
