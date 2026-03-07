package com.lumen.core.memory

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.MemoryEntry
import com.lumen.core.database.entities.MyObjectBox
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class IntentRetrieverTest {

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File

    private val fakeEmbeddingClient = object : EmbeddingClient {
        override suspend fun embed(text: String): FloatArray {
            val seed = text.hashCode()
            return FloatArray(1536) { i -> ((seed + i) % 100) / 100f }
        }
        override suspend fun embedBatch(texts: List<String>): List<FloatArray> =
            texts.map { embed(it) }
    }

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "objectbox-test-${System.nanoTime()}")
        tempDir.mkdirs()
        val store = MyObjectBox.builder()
            .baseDirectory(tempDir)
            .build()
        db = LumenDatabase(store)
    }

    @AfterTest
    fun teardown() {
        db.close()
        tempDir.deleteRecursively()
    }

    private fun seedEntries() {
        val entries = listOf(
            "User is researching machine learning papers",
            "User has a meeting with advisor on Friday",
            "User's research deadline is next month",
            "User likes coffee in the morning",
            "User studies at the university library",
        )
        for (content in entries) {
            runBlocking {
                val embedding = fakeEmbeddingClient.embed(content)
                val entry = MemoryEntry(
                    content = content,
                    embedding = embedding,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
                db.memoryEntryBox.put(entry)
            }
        }
    }

    @Test
    fun retrieve_shortQuery_skipsDecomposition() = runBlocking {
        seedEntries()

        var llmCalled = false
        val fakeLlm = LlmCall { _, _ ->
            llmCalled = true
            """{"sub_queries": ["should not be called"]}"""
        }

        val retriever = IntentRetriever(db, fakeLlm, fakeEmbeddingClient, shortQueryThreshold = 10)
        val results = retriever.retrieve("coffee", limit = 3)

        assertTrue(!llmCalled, "LLM should not be called for short queries")
        assertTrue(results.isNotEmpty())
        assertTrue(results.size <= 3)
    }

    @Test
    fun retrieve_longQuery_decomposesViaLlm() = runBlocking {
        seedEntries()

        var llmCalled = false
        val fakeLlm = LlmCall { _, _ ->
            llmCalled = true
            """{"sub_queries": ["machine learning papers", "research deadline", "advisor meeting"]}"""
        }

        val retriever = IntentRetriever(db, fakeLlm, fakeEmbeddingClient, shortQueryThreshold = 3)
        val results = retriever.retrieve(
            "What do I know about my research activities and upcoming deadlines",
            limit = 5,
        )

        assertTrue(llmCalled, "LLM should be called for long queries")
        assertTrue(results.isNotEmpty())
    }

    @Test
    fun retrieve_deduplicatesResults() = runBlocking {
        seedEntries()

        val fakeLlm = LlmCall { _, _ ->
            """{"sub_queries": ["coffee morning", "coffee"]}"""
        }

        val retriever = IntentRetriever(db, fakeLlm, fakeEmbeddingClient, shortQueryThreshold = 1)
        val results = retriever.retrieve(
            "Tell me about my coffee drinking habits and preferences",
            limit = 5,
        )

        val ids = results.map { it.id }
        assertEquals(ids.distinct().size, ids.size, "Results should contain no duplicate entries")
    }

    @Test
    fun retrieve_ranksMultiHitEntriesHigher() = runBlocking {
        val commonEmbedding = FloatArray(1536) { i -> (1f + i * 0.001f) }
        val commonEntry = MemoryEntry(
            content = "User researches ML and has deadlines",
            embedding = commonEmbedding,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        db.memoryEntryBox.put(commonEntry)

        val otherEmbedding = FloatArray(1536) { i ->
            kotlin.math.sin(50f * 0.1f + i * 50f * 0.01f).toFloat()
        }
        val otherEntry = MemoryEntry(
            content = "User likes pizza",
            embedding = otherEmbedding,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        db.memoryEntryBox.put(otherEntry)

        val fakeLlm = LlmCall { _, _ ->
            """{"sub_queries": ["ML research", "research deadlines"]}"""
        }

        val fakeEmbed = object : EmbeddingClient {
            override suspend fun embed(text: String): FloatArray {
                return FloatArray(1536) { i -> (1f + i * 0.001f) }
            }
            override suspend fun embedBatch(texts: List<String>): List<FloatArray> =
                texts.map { embed(it) }
        }

        val retriever = IntentRetriever(db, fakeLlm, fakeEmbed, shortQueryThreshold = 1)
        val results = retriever.retrieve(
            "What are my research topics and when are deadlines due",
            limit = 5,
        )

        assertTrue(results.isNotEmpty())
        assertEquals(commonEntry.id, results.first().id, "Multi-hit entry should rank first")
    }

    @Test
    fun retrieve_malformedLlmResponse_fallsBackToDirectSearch() = runBlocking {
        seedEntries()

        val fakeLlm = LlmCall { _, _ -> "This is not valid JSON at all" }

        val retriever = IntentRetriever(db, fakeLlm, fakeEmbeddingClient, shortQueryThreshold = 1)
        val results = retriever.retrieve(
            "Tell me everything about my research and academic activities",
            limit = 3,
        )

        assertTrue(results.isNotEmpty(), "Should fall back to direct search on malformed LLM response")
        assertTrue(results.size <= 3)
    }

    @Test
    fun decompose_parsesValidJson() = runBlocking {
        val retriever = IntentRetriever(db, LlmCall { _, _ -> "" }, fakeEmbeddingClient)
        val result = retriever.parseSubQueries(
            """{"sub_queries": ["research papers", "deadlines", "meetings"]}""",
        )

        assertEquals(listOf("research papers", "deadlines", "meetings"), result)
    }

    @Test
    fun decompose_capsAtMaxSubQueries() = runBlocking {
        val retriever = IntentRetriever(
            db, LlmCall { _, _ -> "" }, fakeEmbeddingClient, maxSubQueries = 2,
        )
        val result = retriever.parseSubQueries(
            """{"sub_queries": ["q1", "q2", "q3", "q4", "q5"]}""",
        )

        assertEquals(2, result?.size, "Should cap at maxSubQueries")
    }

    @Test
    fun decompose_emptySubQueries_returnsNull() = runBlocking {
        val retriever = IntentRetriever(db, LlmCall { _, _ -> "" }, fakeEmbeddingClient)
        val result = retriever.parseSubQueries("""{"sub_queries": []}""")

        assertEquals(null, result, "Empty sub_queries should return null")
    }
}
