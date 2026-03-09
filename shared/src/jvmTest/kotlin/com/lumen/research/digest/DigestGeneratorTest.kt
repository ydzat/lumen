package com.lumen.research.digest

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.MyObjectBox
import com.lumen.core.database.entities.ResearchProject
import com.lumen.core.database.entities.Source
import com.lumen.core.memory.EmbeddingClient
import com.lumen.core.memory.IntentRetriever
import com.lumen.core.memory.LlmCall
import com.lumen.core.memory.MemoryManager
import com.lumen.core.memory.SemanticCompressor
import com.lumen.core.memory.SemanticSynthesizer
import com.lumen.core.util.dateToEpochRange
import com.lumen.research.collector.AnalysisStatus
import com.lumen.research.ProjectManager
import com.lumen.research.spark.Spark
import com.lumen.research.spark.SparkEngine
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DigestGeneratorTest {

    private lateinit var db: LumenDatabase
    private lateinit var memoryDb: LumenDatabase
    private lateinit var tempDir: File
    private lateinit var memoryTempDir: File
    private var llmCallCount = 0

    private val fakeLlmCall = LlmCall { _, _ ->
        llmCallCount++
        """{"title": "AI Research Digest — 2026-03-07", "overview": "Today's research focuses on transformer architectures and alignment methods.", "sections": [{"category": "All", "highlights": "1. Transformer advances\n2. Alignment methods", "trends": "Growing interest in efficient fine-tuning techniques.", "insight": "Cross-cutting observation."}]}"""
    }

    private val noopLlmCall = LlmCall { _, _ -> "[]" }

    private val fakeEmbeddingClient = object : EmbeddingClient {
        override suspend fun embed(text: String): FloatArray = FloatArray(384) { 0.1f }
        override suspend fun embedBatch(texts: List<String>): List<FloatArray> = texts.map { embed(it) }
    }

    private val testDate = "2026-03-07"
    private val testDateStartEpoch = dateToEpochRange(testDate).first

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "objectbox-test-${System.nanoTime()}")
        tempDir.mkdirs()
        val store = MyObjectBox.builder().baseDirectory(tempDir).build()
        db = LumenDatabase(store)

        memoryTempDir = File(System.getProperty("java.io.tmpdir"), "objectbox-mem-${System.nanoTime()}")
        memoryTempDir.mkdirs()
        val memStore = MyObjectBox.builder().baseDirectory(memoryTempDir).build()
        memoryDb = LumenDatabase(memStore)

        llmCallCount = 0
    }

    @AfterTest
    fun teardown() {
        db.close()
        memoryDb.close()
        tempDir.deleteRecursively()
        memoryTempDir.deleteRecursively()
    }

    private fun buildMemoryManager(): MemoryManager {
        val compressor = SemanticCompressor(noopLlmCall)
        val synthesizer = SemanticSynthesizer(memoryDb, noopLlmCall, fakeEmbeddingClient)
        val intentRetriever = IntentRetriever(memoryDb, noopLlmCall, fakeEmbeddingClient)
        return MemoryManager(memoryDb, fakeEmbeddingClient, compressor, synthesizer, intentRetriever)
    }

    private fun seedSource(): Long {
        return db.sourceBox.put(Source(name = "arXiv CS.AI", url = "https://arxiv.org", type = "rss"))
    }

    private fun seedArticle(sourceId: Long, title: String, score: Float = 0.5f, projectIds: String = "", aiSummary: String = "Summary of $title", summary: String = "", analysisStatus: String = AnalysisStatus.ANALYZED): Long {
        return db.articleBox.put(Article(
            sourceId = sourceId,
            title = title,
            url = "https://example.com/${title.lowercase().replace(" ", "-")}",
            aiSummary = aiSummary,
            summary = summary,
            aiRelevanceScore = score,
            keywords = "ai,research",
            fetchedAt = testDateStartEpoch + 3600_000L,
            projectIds = projectIds,
            analysisStatus = analysisStatus,
        ))
    }

    @Test
    fun generate_withAnalyzedArticles_producesDigest() = runBlocking {
        val sourceId = seedSource()
        seedArticle(sourceId, "Transformer Advances", score = 0.9f)
        seedArticle(sourceId, "Alignment Methods", score = 0.7f)

        val generator = DigestGenerator(db, fakeLlmCall, null)
        val digest = generator.generate(testDate)

        assertEquals(testDate, digest.date)
        assertTrue(digest.title.isNotBlank())
        assertTrue(digest.content.isNotBlank())
        assertTrue(digest.sourceBreakdown.isNotBlank())
        assertEquals(0L, digest.projectId)
        assertTrue(digest.createdAt > 0)
        assertEquals(1, llmCallCount)
    }

    @Test
    fun generate_idempotent_returnsSameDigest() = runBlocking {
        val sourceId = seedSource()
        seedArticle(sourceId, "Paper 1")

        val generator = DigestGenerator(db, fakeLlmCall, null)
        val first = generator.generate(testDate)
        val second = generator.generate(testDate)

        assertEquals(first.id, second.id)
        assertEquals(first.title, second.title)
        assertEquals(1, llmCallCount)
    }

    @Test
    fun generate_withProjectFilter_onlyIncludesProjectArticles() = runBlocking {
        val sourceId = seedSource()
        seedArticle(sourceId, "Project A Paper", score = 0.9f, projectIds = "1")
        seedArticle(sourceId, "Project B Paper", score = 0.8f, projectIds = "2")
        seedArticle(sourceId, "Unassigned Paper", score = 0.7f)

        val generator = DigestGenerator(db, fakeLlmCall, null)
        val articles = generator.collectArticles(testDate, projectId = 1)

        assertEquals(1, articles.size)
        assertEquals("Project A Paper", articles[0].title)
    }

    @Test
    fun generate_storesHighlightsToMemory() = runBlocking {
        val sourceId = seedSource()
        seedArticle(sourceId, "Important Paper")

        val memoryManager = buildMemoryManager()
        val generator = DigestGenerator(db, fakeLlmCall, memoryManager)
        generator.generate(testDate)

        val memories = memoryDb.memoryEntryBox.all
        assertEquals(1, memories.size)
        assertEquals("research_digest", memories[0].category)
        assertEquals("digest", memories[0].source)
        assertTrue(memories[0].content.contains("Research digest for $testDate"))
    }

    @Test
    fun generate_withNullMemoryManager_skipsMemoryStorage() = runBlocking {
        val sourceId = seedSource()
        seedArticle(sourceId, "Some Paper")

        val generator = DigestGenerator(db, fakeLlmCall, null)
        generator.generate(testDate)

        val memories = memoryDb.memoryEntryBox.all
        assertEquals(0, memories.size)
    }

    @Test
    fun generate_withNoArticles_producesEmptyDigest() = runBlocking {
        val generator = DigestGenerator(db, fakeLlmCall, null)
        val digest = generator.generate(testDate)

        assertTrue(digest.title.contains("No articles"))
        assertEquals(0, llmCallCount)
    }

    @Test
    fun generate_gracefulDegradationOnLlmFailure() = runBlocking {
        val sourceId = seedSource()
        seedArticle(sourceId, "Fallback Paper", score = 0.8f)

        val failingLlm = LlmCall { _, _ -> throw RuntimeException("LLM unavailable") }
        val generator = DigestGenerator(db, failingLlm, null)
        val digest = generator.generate(testDate)

        assertTrue(digest.title.contains("Research Digest"))
        assertTrue(digest.content.isNotBlank())
        // Fallback produces overview with category summaries
        assertTrue(digest.projectSections.contains("Fallback Paper"))
    }

    @Test
    fun buildSourceBreakdown_groupsBySource() = runBlocking {
        val source1 = db.sourceBox.put(Source(name = "arXiv", url = "https://arxiv.org", type = "rss"))
        val source2 = db.sourceBox.put(Source(name = "HN", url = "https://hn.com", type = "rss"))
        seedArticle(source1, "Paper A", score = 0.9f)
        seedArticle(source1, "Paper B", score = 0.5f)
        seedArticle(source2, "Post C", score = 0.8f)

        val generator = DigestGenerator(db, fakeLlmCall, null)
        val articles = generator.collectArticles(testDate, 0)
        val breakdown = generator.buildSourceBreakdown(articles)

        assertEquals(2, breakdown.size)
        val arxivEntry = breakdown.first { it.source == "arXiv" }
        assertEquals(2, arxivEntry.count)
        assertEquals("Paper A", arxivEntry.topArticle)
    }

    @Test
    fun parseDigestResponse_validJson() {
        val generator = DigestGenerator(db, fakeLlmCall, null)
        val response = """{"title": "Daily Digest", "overview": "Key findings.", "sections": [{"category": "All", "highlights": "1. Finding", "trends": "Emerging trends.", "insight": "Observation"}]}"""
        val parsed = generator.parseDigestResponse(response)

        assertEquals("Daily Digest", parsed.title)
        assertTrue(parsed.overview.contains("Key findings."))
        assertEquals(1, parsed.sections.size)
        assertEquals("All", parsed.sections[0].category)
    }

    @Test
    fun parseDigestResponse_invalidJson_returnsEmpty() {
        val generator = DigestGenerator(db, fakeLlmCall, null)
        val parsed = generator.parseDigestResponse("not json")

        assertEquals("", parsed.title)
        assertEquals("", parsed.overview)
    }

    @Test
    fun dateToEpochRange_correctBounds() {
        val (start, end) = dateToEpochRange("2026-03-07")
        assertEquals(86_400_000L, end - start)
        assertTrue(start > 0)

        val (invalidStart, invalidEnd) = dateToEpochRange("invalid")
        assertEquals(0L, invalidStart)
        assertEquals(0L, invalidEnd)
    }

    // --- New tests for multi-project overhaul ---

    @Test
    fun collectArticles_onlyIncludesAnalyzedArticles() = runBlocking {
        val sourceId = seedSource()
        seedArticle(sourceId, "Analyzed Article", analysisStatus = AnalysisStatus.ANALYZED)
        seedArticle(sourceId, "Embedded Only", analysisStatus = AnalysisStatus.EMBEDDED)
        seedArticle(sourceId, "New Article", analysisStatus = AnalysisStatus.NEW)

        val generator = DigestGenerator(db, fakeLlmCall, null)
        val articles = generator.collectArticles(testDate, 0)

        assertEquals(1, articles.size)
        assertEquals("Analyzed Article", articles[0].title)
    }

    @Test
    fun buildProjectSections_groupsByProject() {
        val sourceId = seedSource()
        seedArticle(sourceId, "Paper A", score = 0.9f, projectIds = "1")
        seedArticle(sourceId, "Paper B", score = 0.8f, projectIds = "1")
        seedArticle(sourceId, "Paper C", score = 0.7f, projectIds = "2")
        seedArticle(sourceId, "Paper D", score = 0.6f, projectIds = "")

        db.researchProjectBox.put(listOf(
            ResearchProject(name = "AI Research", keywords = "ai", isActive = true),
            ResearchProject(name = "HPC", keywords = "hpc", isActive = true),
        ))
        val projectManager = ProjectManager(db, fakeEmbeddingClient)

        val generator = DigestGenerator(db, fakeLlmCall, null, null, projectManager)
        val articles = generator.collectArticles(testDate, 0)
        val sections = generator.buildProjectSections(articles)

        assertTrue(sections.isNotEmpty())
        val aiSection = sections.find { it.projectName == "AI Research" }
        assertTrue(aiSection != null)
        assertEquals(2, aiSection.articleCount)
        assertTrue(aiSection.highlights.contains("Paper A"))

        val hpcSection = sections.find { it.projectName == "HPC" }
        assertTrue(hpcSection != null)
        assertEquals(1, hpcSection.articleCount)

        val unassigned = sections.find { it.projectName == "Unassigned" }
        assertTrue(unassigned != null)
        assertEquals(1, unassigned.articleCount)
    }

    @Test
    fun generate_unifiedDigest_includesProjectSections() = runBlocking {
        val sourceId = seedSource()
        seedArticle(sourceId, "Paper A", score = 0.9f, projectIds = "1")
        seedArticle(sourceId, "Paper B", score = 0.7f, projectIds = "2")

        db.researchProjectBox.put(listOf(
            ResearchProject(name = "AI Research", keywords = "ai", isActive = true),
            ResearchProject(name = "HPC", keywords = "hpc", isActive = true),
        ))
        val projectManager = ProjectManager(db, fakeEmbeddingClient)

        val generator = DigestGenerator(db, fakeLlmCall, null, null, projectManager)
        val digest = generator.generate(testDate)

        assertTrue(digest.projectSections.isNotBlank())
        assertTrue(digest.projectSections.contains("AI Research"))
        assertTrue(digest.projectSections.contains("HPC"))
    }

    @Test
    fun generate_unifiedDigest_includesSparks() = runBlocking {
        val sourceId = seedSource()
        seedArticle(sourceId, "Paper A", score = 0.9f)
        // Seed article matching spark keyword for text search
        db.articleBox.put(Article(
            sourceId = sourceId,
            title = "Parallel AI Computing",
            summary = "Using parallel AI for HPC workloads",
            keywords = "parallel AI",
            aiSummary = "Parallel AI summary",
            fetchedAt = testDateStartEpoch + 3600_000L,
            analysisStatus = AnalysisStatus.ANALYZED,
        ))

        db.researchProjectBox.put(listOf(
            ResearchProject(name = "AI Research", keywords = "ai", isActive = true),
            ResearchProject(name = "HPC", keywords = "hpc", isActive = true),
        ))
        val projectManager = ProjectManager(db, fakeEmbeddingClient)

        var sparkCallCount = 0
        val sparkLlm = LlmCall { _, _ ->
            sparkCallCount++
            if (sparkCallCount == 1) {
                """{"keywords": ["parallel AI"]}"""
            } else {
                """{"sparks": [{"title": "Cross Insight", "description": "AI meets HPC", "relatedKeywords": ["parallel"], "sourceProjectIds": [1, 2]}]}"""
            }
        }
        val sparkEngine = SparkEngine(sparkLlm, db)

        val generator = DigestGenerator(db, fakeLlmCall, null, sparkEngine, projectManager)
        val digest = generator.generate(testDate)

        assertTrue(digest.sparks.isNotBlank())
        assertTrue(digest.sparks.contains("Cross Insight"))
    }

    @Test
    fun generate_unifiedDigest_withNoSparkEngine_handlesGracefully() = runBlocking {
        val sourceId = seedSource()
        seedArticle(sourceId, "Paper A", score = 0.9f)

        val generator = DigestGenerator(db, fakeLlmCall, null, null, null)
        val digest = generator.generate(testDate)

        // Without SparkEngine, sparks depend on LLM category matching.
        // If categories don't match auto-categorized names, sparks may be empty.
        // The key point: generation completes without error.
        assertTrue(digest.title.isNotBlank())
    }

    @Test
    fun generate_singleProject_noProjectSections() = runBlocking {
        val sourceId = seedSource()
        seedArticle(sourceId, "Paper A", score = 0.9f, projectIds = "1")

        val generator = DigestGenerator(db, fakeLlmCall, null)
        val digest = generator.generate(testDate, projectId = 1)

        assertEquals("", digest.projectSections)
        assertEquals("", digest.sparks)
        assertEquals(1L, digest.projectId)
    }

    @Test
    fun buildUserPrompt_usesSummaryFallback() {
        val sourceId = seedSource()
        seedArticle(sourceId, "Embedding Only Paper", aiSummary = "", summary = "Fallback summary text")

        val generator = DigestGenerator(db, fakeLlmCall, null)
        val articles = generator.collectArticles(testDate, 0)
        val sections = listOf(
            DigestGenerator.ProjectSection(
                projectName = "All",
                articleCount = articles.size,
                articles = articles.map { article ->
                    DigestGenerator.ArticleReference(
                        articleId = article.id,
                        title = article.title,
                        url = article.url,
                        relevanceScore = article.aiRelevanceScore,
                        summarySnippet = (article.aiSummary.ifBlank { article.summary }).take(100),
                    )
                },
            ),
        )
        val prompt = generator.buildUserPrompt(sections, testDate, articles.size)

        assertTrue(prompt.contains("Fallback summary text"))
    }

    @Test
    fun buildSparkSections_convertsSparks() {
        val generator = DigestGenerator(db, fakeLlmCall, null)
        val sparks = listOf(
            Spark("Insight 1", "Description 1", listOf("kw1"), listOf(1, 2)),
            Spark("Insight 2", "Description 2", listOf("kw2", "kw3"), listOf(2, 3)),
        )

        val sections = generator.buildSparkSections(sparks)

        assertEquals(2, sections.size)
        assertEquals("Insight 1", sections[0].title)
        assertEquals("Description 1", sections[0].description)
        assertEquals(listOf("kw1"), sections[0].relatedKeywords)
        assertEquals("Insight 2", sections[1].title)
        assertEquals(listOf("kw2", "kw3"), sections[1].relatedKeywords)
    }
}
