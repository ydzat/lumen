package com.lumen.research.digest

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.MyObjectBox
import com.lumen.core.database.entities.Source
import com.lumen.core.memory.EmbeddingClient
import com.lumen.core.memory.IntentRetriever
import com.lumen.core.memory.LlmCall
import com.lumen.core.memory.MemoryManager
import com.lumen.core.memory.SemanticCompressor
import com.lumen.core.memory.SemanticSynthesizer
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
        """{"title": "AI Research Digest — 2026-03-07", "highlights": "Today's research focuses on transformer architectures and alignment methods.", "trends": "Growing interest in efficient fine-tuning techniques."}"""
    }

    private val noopLlmCall = LlmCall { _, _ -> "[]" }

    private val fakeEmbeddingClient = object : EmbeddingClient {
        override suspend fun embed(text: String): FloatArray = FloatArray(384) { 0.1f }
        override suspend fun embedBatch(texts: List<String>): List<FloatArray> = texts.map { embed(it) }
    }

    private val testDate = "2026-03-07"
    private val testDateStartEpoch = DigestGenerator.dateToEpochRange(testDate).first

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

    private fun seedArticle(sourceId: Long, title: String, score: Float = 0.5f, projectIds: String = ""): Long {
        return db.articleBox.put(Article(
            sourceId = sourceId,
            title = title,
            url = "https://example.com/${title.lowercase().replace(" ", "-")}",
            aiSummary = "Summary of $title",
            aiRelevanceScore = score,
            keywords = "ai,research",
            fetchedAt = testDateStartEpoch + 3600_000L,
            projectIds = projectIds,
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
        assertTrue(digest.content.contains("Fallback Paper"))
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
        val response = """{"title": "Daily Digest", "highlights": "Key findings.", "trends": "Emerging trends."}"""
        val (title, content) = generator.parseDigestResponse(response)

        assertEquals("Daily Digest", title)
        assertTrue(content.contains("Key findings."))
        assertTrue(content.contains("Emerging trends."))
    }

    @Test
    fun parseDigestResponse_invalidJson_returnsEmpty() {
        val generator = DigestGenerator(db, fakeLlmCall, null)
        val (title, content) = generator.parseDigestResponse("not json")

        assertEquals("", title)
        assertEquals("", content)
    }

    @Test
    fun dateToEpochRange_correctBounds() {
        val (start, end) = DigestGenerator.dateToEpochRange("2026-03-07")
        assertEquals(86_400_000L, end - start)
        assertTrue(start > 0)

        val (invalidStart, invalidEnd) = DigestGenerator.dateToEpochRange("invalid")
        assertEquals(0L, invalidStart)
        assertEquals(0L, invalidEnd)
    }
}
