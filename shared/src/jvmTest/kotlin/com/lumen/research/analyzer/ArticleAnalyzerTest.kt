package com.lumen.research.analyzer

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.MyObjectBox
import com.lumen.core.memory.EmbeddingClient
import com.lumen.core.memory.LlmCall
import com.lumen.research.collector.AnalysisStatus
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArticleAnalyzerTest {

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File
    private lateinit var analyzer: ArticleAnalyzer
    private var llmCallCount = 0
    private var embedCallCount = 0

    private val fakeLlmCall = LlmCall { _, _ ->
        llmCallCount++
        """{"summary": "This paper presents a novel approach to AI safety.", "keywords": ["AI", "safety", "alignment"]}"""
    }

    private val fakeEmbeddingClient = object : EmbeddingClient {
        override suspend fun embed(text: String): FloatArray {
            embedCallCount++
            return FloatArray(384) { text.hashCode().toFloat() / Int.MAX_VALUE }
        }

        override suspend fun embedBatch(texts: List<String>): List<FloatArray> {
            return texts.map { embed(it) }
        }
    }

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "objectbox-test-${System.nanoTime()}")
        tempDir.mkdirs()
        val store = MyObjectBox.builder()
            .baseDirectory(tempDir)
            .build()
        db = LumenDatabase(store)
        analyzer = ArticleAnalyzer(db, fakeLlmCall, fakeEmbeddingClient)
        llmCallCount = 0
        embedCallCount = 0
    }

    @AfterTest
    fun teardown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun analyze_generatesAiSummaryAndKeywords() = runBlocking {
        val articleId = db.articleBox.put(Article(
            title = "AI Safety Research",
            url = "https://example.com/paper",
            summary = "A paper about AI safety",
        ))

        val result = analyzer.analyze(db.articleBox.get(articleId))

        assertEquals("This paper presents a novel approach to AI safety.", result.aiSummary)
        assertEquals("AI,safety,alignment", result.keywords)
    }

    @Test
    fun analyze_computesEmbedding() = runBlocking {
        val articleId = db.articleBox.put(Article(
            title = "Test Paper",
            url = "https://example.com/paper",
            summary = "Test summary",
        ))

        val result = analyzer.analyze(db.articleBox.get(articleId))

        assertTrue(result.embedding.isNotEmpty())
        assertEquals(384, result.embedding.size)
        assertEquals(1, embedCallCount)
    }

    @Test
    fun analyze_persistsResults() = runBlocking {
        val articleId = db.articleBox.put(Article(
            title = "Persisted Paper",
            url = "https://example.com/paper",
            summary = "Test",
        ))

        analyzer.analyze(db.articleBox.get(articleId))

        val persisted = db.articleBox.get(articleId)
        assertTrue(persisted.aiSummary.isNotBlank())
        assertTrue(persisted.keywords.isNotBlank())
        assertTrue(persisted.embedding.isNotEmpty())
    }

    @Test
    fun analyze_gracefulDegradationOnLlmFailure() = runBlocking {
        val failingLlm = LlmCall { _, _ -> throw RuntimeException("LLM unavailable") }
        val failAnalyzer = ArticleAnalyzer(db, failingLlm, fakeEmbeddingClient)

        val articleId = db.articleBox.put(Article(
            title = "Fallback Paper",
            url = "https://example.com/paper",
            summary = "Original summary text",
        ))

        val result = failAnalyzer.analyze(db.articleBox.get(articleId))

        assertEquals("Original summary text", result.aiSummary)
        assertEquals("", result.keywords)
        assertTrue(result.embedding.isNotEmpty())
    }

    @Test
    fun analyzeBatch_skipsAlreadyAnalyzed() = runBlocking {
        db.articleBox.put(Article(
            title = "Already Analyzed",
            url = "https://example.com/1",
            aiSummary = "Existing summary",
        ))
        db.articleBox.put(Article(
            title = "Needs Analysis",
            url = "https://example.com/2",
        ))

        val articles = db.articleBox.all
        val results = analyzer.analyzeBatch(articles)

        assertEquals(1, results.size)
        assertEquals("Needs Analysis", results[0].title)
        assertEquals(1, llmCallCount)
    }

    @Test
    fun analyzeBatch_processesMultipleArticles() = runBlocking {
        db.articleBox.put(Article(title = "Paper 1", url = "https://example.com/1"))
        db.articleBox.put(Article(title = "Paper 2", url = "https://example.com/2"))
        db.articleBox.put(Article(title = "Paper 3", url = "https://example.com/3"))

        val results = analyzer.analyzeBatch(db.articleBox.all)

        assertEquals(3, results.size)
        assertEquals(3, llmCallCount)
        assertTrue(results.all { it.aiSummary.isNotBlank() })
    }

    @Test
    fun parseAnalysisResponse_validJson() {
        val response = """{"summary": "A great paper.", "keywords": ["ml", "nlp"]}"""
        val result = analyzer.parseAnalysisResponse(response)

        assertEquals("A great paper.", result.summary)
        assertEquals(listOf("ml", "nlp"), result.keywords)
    }

    @Test
    fun parseAnalysisResponse_jsonWithSurroundingText() {
        val response = """Here is the analysis: {"summary": "Result.", "keywords": ["ai"]} End."""
        val result = analyzer.parseAnalysisResponse(response)

        assertEquals("Result.", result.summary)
        assertEquals(listOf("ai"), result.keywords)
    }

    @Test
    fun parseAnalysisResponse_invalidJson_returnsEmpty() {
        val result = analyzer.parseAnalysisResponse("not json at all")

        assertEquals("", result.summary)
        assertEquals(emptyList(), result.keywords)
    }

    @Test
    fun parseAnalysisResponse_ignoresUnknownFields() {
        val response = """{"summary": "Summary.", "keywords": ["ai"], "extraField": "ignored"}"""
        val result = analyzer.parseAnalysisResponse(response)

        assertEquals("Summary.", result.summary)
        assertEquals(listOf("ai"), result.keywords)
    }

    @Test
    fun buildUserPrompt_stripsHtmlFromContent() {
        val article = Article(
            title = "Test",
            summary = "A summary",
            content = "<h2>Section</h2><p>Some <strong>bold</strong> text</p>",
        )
        val prompt = analyzer.buildUserPrompt(article)

        assertTrue(prompt.contains("Title: Test"))
        assertTrue(prompt.contains("Summary: A summary"))
        assertTrue(prompt.contains("Content excerpt:"))
        // HTML tags should be stripped
        assertTrue(!prompt.contains("<h2>"))
        assertTrue(!prompt.contains("<strong>"))
        assertTrue(prompt.contains("bold"))
    }

    @Test
    fun embedOnly_setsEmbeddingAndStatus_noLlmCall() = runBlocking {
        val articleId = db.articleBox.put(Article(
            title = "Embed Only Paper",
            url = "https://example.com/embed",
            summary = "Summary text",
        ))

        val result = analyzer.embedOnly(db.articleBox.get(articleId))

        assertTrue(result.embedding.isNotEmpty())
        assertEquals(384, result.embedding.size)
        assertEquals(AnalysisStatus.EMBEDDED, result.analysisStatus)
        assertEquals(1, embedCallCount)
        assertEquals(0, llmCallCount)
    }

    @Test
    fun analyzeWithLlm_setsSummaryAndStatus_noEmbedding() = runBlocking {
        val articleId = db.articleBox.put(Article(
            title = "LLM Only Paper",
            url = "https://example.com/llm",
            summary = "Summary text",
            embedding = FloatArray(384) { 0.1f },
            analysisStatus = AnalysisStatus.EMBEDDED,
        ))

        val result = analyzer.analyzeWithLlm(db.articleBox.get(articleId))

        assertEquals("This paper presents a novel approach to AI safety.", result.aiSummary)
        assertEquals("AI,safety,alignment", result.keywords)
        assertEquals(AnalysisStatus.ANALYZED, result.analysisStatus)
        assertEquals(1, llmCallCount)
        assertEquals(0, embedCallCount)
    }

    @Test
    fun analyze_delegatesToBothMethods() = runBlocking {
        val articleId = db.articleBox.put(Article(
            title = "Full Analysis Paper",
            url = "https://example.com/full",
            summary = "Summary text",
        ))

        val result = analyzer.analyze(db.articleBox.get(articleId))

        assertTrue(result.embedding.isNotEmpty())
        assertTrue(result.aiSummary.isNotBlank())
        assertEquals(AnalysisStatus.ANALYZED, result.analysisStatus)
        assertEquals(1, embedCallCount)
        assertEquals(1, llmCallCount)
    }
}
