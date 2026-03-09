package com.lumen.companion.agent

import com.lumen.companion.agent.tools.RecallMemoryArgs
import com.lumen.companion.agent.tools.RecallMemoryTool
import com.lumen.companion.agent.tools.StoreMemoryArgs
import com.lumen.companion.agent.tools.StoreMemoryTool
import com.lumen.core.config.LlmConfig
import com.lumen.core.database.entities.EMBEDDING_DIMENSIONS
import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.MyObjectBox
import com.lumen.core.memory.EmbeddingClient
import com.lumen.core.memory.IntentRetriever
import com.lumen.core.memory.LlmCall
import com.lumen.core.memory.MemoryManager
import com.lumen.core.memory.SemanticCompressor
import com.lumen.core.memory.SemanticSynthesizer
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class MemoryToolsTest {

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File
    private lateinit var memoryManager: MemoryManager

    private val fakeEmbeddingClient = object : EmbeddingClient {
        override suspend fun embed(text: String): FloatArray {
            val seed = text.hashCode()
            return FloatArray(EMBEDDING_DIMENSIONS) { i -> ((seed + i) % 100) / 100f }
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
        val fakeCompressor = SemanticCompressor(LlmCall { _, _ -> "[]" })
        val noopSynthesizer = SemanticSynthesizer(
            db, LlmCall { _, _ -> "" }, fakeEmbeddingClient, similarityThreshold = 1.0f,
        )
        val directRetriever = IntentRetriever(
            db, LlmCall { _, _ -> "" }, fakeEmbeddingClient, shortQueryThreshold = Int.MAX_VALUE,
        )
        memoryManager = MemoryManager(db, fakeEmbeddingClient, fakeCompressor, noopSynthesizer, directRetriever)
    }

    @AfterTest
    fun teardown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun recallMemoryTool_execute_returnsFormattedResults() = runBlocking {
        memoryManager.store("User likes coffee", "preference", "conversation")
        memoryManager.store("User studies AI", "fact", "conversation")

        val tool = RecallMemoryTool(memoryManager)
        val result = tool.execute(RecallMemoryArgs(query = "coffee", limit = 5))

        assertTrue(result.contains("- "), "Result should contain formatted entries")
    }

    @Test
    fun recallMemoryTool_execute_emptyResults_returnsNoMemories() = runBlocking {
        val tool = RecallMemoryTool(memoryManager)
        val result = tool.execute(RecallMemoryArgs(query = "nonexistent", limit = 5))

        assertEquals("No memories found.", result)
    }

    @Test
    fun storeMemoryTool_execute_persistsAndReturnsConfirmation() = runBlocking {
        val tool = StoreMemoryTool(memoryManager)
        val result = tool.execute(StoreMemoryArgs(content = "User likes tea", category = "preference"))

        assertTrue(result.startsWith("Memory stored"), "Result should confirm storage")
        assertTrue(result.contains("User likes tea"))

        val entries = memoryManager.recall("tea", limit = 5)
        assertTrue(entries.any { it.content == "User likes tea" })
    }

    @Test
    fun storeMemoryTool_execute_usesDefaultCategory() = runBlocking {
        val tool = StoreMemoryTool(memoryManager)
        tool.execute(StoreMemoryArgs(content = "Some general info"))

        val entries = memoryManager.recall("general info", limit = 5)
        assertTrue(entries.any { it.category == "general" })
    }

    @Test
    fun lumenAgent_withNullMemory_hasNoTools() {
        val agent = LumenAgent(LlmConfig(apiKey = "test"))
        try {
            assertTrue(agent.tools.isEmpty(), "Agent without memory should have no tools")
        } finally {
            agent.close()
        }
    }

    @Test
    fun lumenAgent_withMemory_hasTools() {
        val agent = LumenAgent(LlmConfig(apiKey = "test"), memoryManager)
        try {
            assertEquals(2, agent.tools.size, "Agent with memory should have 2 tools")
            assertTrue(agent.tools.any { it.name == "recall_memory" })
            assertTrue(agent.tools.any { it.name == "store_memory" })
        } finally {
            agent.close()
        }
    }
}
