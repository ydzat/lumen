package com.lumen.research.spark

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.MyObjectBox
import com.lumen.core.database.entities.ResearchProject
import com.lumen.core.memory.EmbeddingClient
import com.lumen.core.memory.LlmCall
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SparkEngineTest {

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File

    private val fakeEmbeddingClient = object : EmbeddingClient {
        override suspend fun embed(text: String): FloatArray = FloatArray(384) { 0.1f }
        override suspend fun embedBatch(texts: List<String>): List<FloatArray> = texts.map { embed(it) }
    }

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "objectbox-spark-test-${System.nanoTime()}")
        tempDir.mkdirs()
        val store = MyObjectBox.builder().baseDirectory(tempDir).build()
        db = LumenDatabase(store)
    }

    @AfterTest
    fun teardown() {
        db.close()
        tempDir.deleteRecursively()
    }

    private fun createProjects(): List<ResearchProject> {
        val p1 = ResearchProject(
            name = "AI Agent Research",
            description = "Tool calling and agent frameworks",
            keywords = "tool calling,agent,LLM,function calling",
            isActive = true,
        )
        val p2 = ResearchProject(
            name = "HPC Scheduling",
            description = "High-performance computing job scheduling",
            keywords = "parallel scheduling,HPC,job queue,resource allocation",
            isActive = true,
        )
        db.researchProjectBox.put(listOf(p1, p2))
        return db.researchProjectBox.all
    }

    // --- generateSearchKeywords ---

    @Test
    fun generateSearchKeywords_withMultipleProjects_returnsKeywords() = runBlocking {
        val projects = createProjects()
        val llmCall = LlmCall { _, _ ->
            """{"keywords": ["parallel tool execution", "distributed agent scheduling", "resource-aware LLM"]}"""
        }
        val engine = SparkEngine(llmCall, db)

        val keywords = engine.generateSearchKeywords(projects)

        assertEquals(3, keywords.size)
        assertTrue(keywords.contains("parallel tool execution"))
        assertTrue(keywords.contains("distributed agent scheduling"))
        assertTrue(keywords.contains("resource-aware LLM"))
    }

    @Test
    fun generateSearchKeywords_withSingleProject_returnsEmpty() = runBlocking {
        val project = ResearchProject(name = "Solo", keywords = "test", isActive = true)
        db.researchProjectBox.put(project)
        val llmCall = LlmCall { _, _ -> """{"keywords": ["should not appear"]}""" }
        val engine = SparkEngine(llmCall, db)

        val keywords = engine.generateSearchKeywords(listOf(db.researchProjectBox.all.first()))

        assertTrue(keywords.isEmpty())
    }

    @Test
    fun generateSearchKeywords_withNoProjects_returnsEmpty() = runBlocking {
        val llmCall = LlmCall { _, _ -> """{"keywords": ["should not appear"]}""" }
        val engine = SparkEngine(llmCall, db)

        val keywords = engine.generateSearchKeywords(emptyList())

        assertTrue(keywords.isEmpty())
    }

    @Test
    fun generateSearchKeywords_llmError_returnsEmpty() = runBlocking {
        val projects = createProjects()
        val llmCall = LlmCall { _, _ -> throw RuntimeException("LLM unavailable") }
        val engine = SparkEngine(llmCall, db)

        val keywords = engine.generateSearchKeywords(projects)

        assertTrue(keywords.isEmpty())
    }

    @Test
    fun generateSearchKeywords_respectsMaxKeywords() = runBlocking {
        val projects = createProjects()
        val manyKeywords = (1..20).joinToString(",") { "\"keyword$it\"" }
        val llmCall = LlmCall { _, _ -> """{"keywords": [$manyKeywords]}""" }
        val engine = SparkEngine(llmCall, db)

        val keywords = engine.generateSearchKeywords(projects)

        assertTrue(keywords.size <= SparkEngine.MAX_KEYWORDS)
    }

    // --- parseKeywordsResponse ---

    @Test
    fun parseKeywordsResponse_validJson() {
        val engine = SparkEngine(LlmCall { _, _ -> "" }, db)
        val result = engine.parseKeywordsResponse("""{"keywords": ["kw1", "kw2", "kw3"]}""")

        assertEquals(3, result.size)
        assertTrue(result.contains("kw1"))
    }

    @Test
    fun parseKeywordsResponse_withMarkdownWrapper() {
        val engine = SparkEngine(LlmCall { _, _ -> "" }, db)
        val result = engine.parseKeywordsResponse("""
            Here are the keywords:
            ```json
            {"keywords": ["cross-domain", "hybrid"]}
            ```
        """.trimIndent())

        assertEquals(2, result.size)
    }

    @Test
    fun parseKeywordsResponse_invalidJson_returnsEmpty() {
        val engine = SparkEngine(LlmCall { _, _ -> "" }, db)
        val result = engine.parseKeywordsResponse("not json at all")

        assertTrue(result.isEmpty())
    }

    @Test
    fun parseKeywordsResponse_filtersBlankKeywords() {
        val engine = SparkEngine(LlmCall { _, _ -> "" }, db)
        val result = engine.parseKeywordsResponse("""{"keywords": ["valid", "", "  ", "also valid"]}""")

        assertEquals(2, result.size)
        assertTrue(result.contains("valid"))
        assertTrue(result.contains("also valid"))
    }

    // --- generateInsights ---

    @Test
    fun generateInsights_withProjectsAndArticles_returnsSparks() = runBlocking {
        val projects = createProjects()
        // Seed articles so text search finds them
        db.articleBox.put(listOf(
            Article(title = "Parallel Tool Execution in LLMs", summary = "Combining parallel scheduling with tool calling", keywords = "parallel tool execution"),
            Article(title = "Agent Job Scheduling", summary = "Scheduling agent tasks on HPC clusters", keywords = "distributed agent scheduling"),
        ))

        var callCount = 0
        val llmCall = LlmCall { _, _ ->
            callCount++
            if (callCount == 1) {
                // First call: generateSearchKeywords
                """{"keywords": ["parallel tool execution", "agent scheduling"]}"""
            } else {
                // Second call: generateInsights
                """{"sparks": [{"title": "Parallel Tool Calling", "description": "Apply HPC scheduling to LLM tool calls", "relatedKeywords": ["parallel", "tool calling"], "sourceProjectIds": [${projects[0].id}, ${projects[1].id}]}]}"""
            }
        }
        val engine = SparkEngine(llmCall, db)

        val sparks = engine.generateInsights(projects)

        assertEquals(1, sparks.size)
        assertEquals("Parallel Tool Calling", sparks[0].title)
        assertEquals("Apply HPC scheduling to LLM tool calls", sparks[0].description)
        assertTrue(sparks[0].relatedKeywords.contains("parallel"))
        assertEquals(2, sparks[0].sourceProjectIds.size)
    }

    @Test
    fun generateInsights_withSingleProject_returnsEmpty() = runBlocking {
        val project = ResearchProject(name = "Solo", keywords = "test", isActive = true)
        db.researchProjectBox.put(project)
        val llmCall = LlmCall { _, _ -> """{"sparks": []}""" }
        val engine = SparkEngine(llmCall, db)

        val sparks = engine.generateInsights(listOf(db.researchProjectBox.all.first()))

        assertTrue(sparks.isEmpty())
    }

    // --- parseInsightsResponse ---

    @Test
    fun parseInsightsResponse_validJson() {
        val engine = SparkEngine(LlmCall { _, _ -> "" }, db)
        val result = engine.parseInsightsResponse("""
            {"sparks": [
                {"title": "Spark 1", "description": "Desc 1", "relatedKeywords": ["a"], "sourceProjectIds": [1, 2]},
                {"title": "Spark 2", "description": "Desc 2", "relatedKeywords": ["b"], "sourceProjectIds": [2, 3]}
            ]}
        """.trimIndent())

        assertEquals(2, result.size)
        assertEquals("Spark 1", result[0].title)
        assertEquals("Desc 2", result[1].description)
    }

    @Test
    fun parseInsightsResponse_filtersEmptyTitles() {
        val engine = SparkEngine(LlmCall { _, _ -> "" }, db)
        val result = engine.parseInsightsResponse("""
            {"sparks": [
                {"title": "", "description": "No title", "relatedKeywords": [], "sourceProjectIds": []},
                {"title": "Valid", "description": "Has title", "relatedKeywords": [], "sourceProjectIds": []}
            ]}
        """.trimIndent())

        assertEquals(1, result.size)
        assertEquals("Valid", result[0].title)
    }

    @Test
    fun parseInsightsResponse_invalidJson_returnsEmpty() {
        val engine = SparkEngine(LlmCall { _, _ -> "" }, db)
        val result = engine.parseInsightsResponse("broken json")

        assertTrue(result.isEmpty())
    }

    // --- realtimeSpark ---

    @Test
    fun realtimeSpark_withTopic_returnsSpark() = runBlocking {
        val projects = createProjects()
        val llmCall = LlmCall { _, _ ->
            """{"title": "Parallel Agent Execution", "description": "Use HPC scheduling for parallel tool calls in agents", "relatedKeywords": ["parallel", "tool calling"], "sourceProjectIds": [${projects[0].id}, ${projects[1].id}]}"""
        }
        val engine = SparkEngine(llmCall, db)

        val spark = engine.realtimeSpark("How to speed up agent tool execution?", projects)

        assertNotNull(spark)
        assertEquals("Parallel Agent Execution", spark.title)
        assertTrue(spark.description.isNotBlank())
        assertEquals(2, spark.sourceProjectIds.size)
    }

    @Test
    fun realtimeSpark_withSingleProject_returnsNull() = runBlocking {
        val project = ResearchProject(name = "Solo", keywords = "test", isActive = true)
        db.researchProjectBox.put(project)
        val llmCall = LlmCall { _, _ -> """{"title": "Should not appear"}""" }
        val engine = SparkEngine(llmCall, db)

        val spark = engine.realtimeSpark("topic", listOf(db.researchProjectBox.all.first()))

        assertNull(spark)
    }

    @Test
    fun realtimeSpark_emptyResponse_returnsNull() = runBlocking {
        val projects = createProjects()
        val llmCall = LlmCall { _, _ ->
            """{"title": "", "description": "", "relatedKeywords": [], "sourceProjectIds": []}"""
        }
        val engine = SparkEngine(llmCall, db)

        val spark = engine.realtimeSpark("topic", projects)

        assertNull(spark)
    }

    @Test
    fun realtimeSpark_llmError_returnsNull() = runBlocking {
        val projects = createProjects()
        val llmCall = LlmCall { _, _ -> throw RuntimeException("LLM error") }
        val engine = SparkEngine(llmCall, db)

        val spark = engine.realtimeSpark("topic", projects)

        assertNull(spark)
    }

    // --- parseRealtimeResponse ---

    @Test
    fun parseRealtimeResponse_validJson() {
        val engine = SparkEngine(LlmCall { _, _ -> "" }, db)
        val result = engine.parseRealtimeResponse(
            """{"title": "Test Spark", "description": "A test insight", "relatedKeywords": ["kw1"], "sourceProjectIds": [1]}"""
        )

        assertNotNull(result)
        assertEquals("Test Spark", result.title)
        assertEquals("A test insight", result.description)
    }

    @Test
    fun parseRealtimeResponse_blankTitle_returnsNull() {
        val engine = SparkEngine(LlmCall { _, _ -> "" }, db)
        val result = engine.parseRealtimeResponse(
            """{"title": "", "description": "no title", "relatedKeywords": [], "sourceProjectIds": []}"""
        )

        assertNull(result)
    }

    // --- Prompt building ---

    @Test
    fun buildKeywordPrompt_includesProjectInfo() {
        val projects = createProjects()
        val engine = SparkEngine(LlmCall { _, _ -> "" }, db)

        val (system, user) = engine.buildKeywordPrompt(projects)

        assertTrue(system.contains("cross-domain"))
        assertTrue(user.contains("AI Agent Research"))
        assertTrue(user.contains("HPC Scheduling"))
        assertTrue(user.contains("tool calling"))
        assertTrue(user.contains("parallel scheduling"))
    }

    @Test
    fun buildInsightPrompt_includesArticles() {
        val projects = createProjects()
        val articles = listOf(
            Article(title = "Test Article", summary = "A summary", aiSummary = "AI summary"),
        )
        val engine = SparkEngine(LlmCall { _, _ -> "" }, db)

        val (system, user) = engine.buildInsightPrompt(projects, articles)

        assertTrue(system.contains("unexpected connections"))
        assertTrue(user.contains("Test Article"))
        assertTrue(user.contains("AI summary"))
    }

    @Test
    fun buildInsightPrompt_fallsBackToSummaryWhenNoAiSummary() {
        val projects = createProjects()
        val articles = listOf(
            Article(title = "Test Article", summary = "Regular summary", aiSummary = ""),
        )
        val engine = SparkEngine(LlmCall { _, _ -> "" }, db)

        val (_, user) = engine.buildInsightPrompt(projects, articles)

        assertTrue(user.contains("Regular summary"))
    }

    @Test
    fun buildRealtimePrompt_includesTopic() {
        val projects = createProjects()
        val engine = SparkEngine(LlmCall { _, _ -> "" }, db)

        val (system, user) = engine.buildRealtimePrompt("parallel execution", projects)

        assertTrue(system.contains("cross-project insight"))
        assertTrue(user.contains("parallel execution"))
        assertTrue(user.contains("AI Agent Research"))
    }
}
