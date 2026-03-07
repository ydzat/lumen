package com.lumen.companion.agent

import com.lumen.core.config.LlmConfig
import com.lumen.core.config.UserPreferences
import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.EMBEDDING_DIMENSIONS
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class MemoryAutoRecallTest {

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File
    private lateinit var memoryManager: MemoryManager

    private val fakeEmbeddingClient = object : EmbeddingClient {
        override suspend fun embed(text: String): FloatArray {
            val seed = text.hashCode()
            return FloatArray(EMBEDDING_DIMENSIONS) { i ->
                ((seed + i) % 100) / 100f
            }
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
    fun buildSystemPromptWithRecall_withMemories_injectsContext(): Unit = runBlocking {
        memoryManager.store("User likes coffee", "preference", "conversation")
        memoryManager.store("User works at RWTH Aachen", "fact", "conversation")

        val agent = LumenAgent(
            config = LlmConfig(apiKey = "test"),
            memoryManager = memoryManager,
            userPreferences = UserPreferences(memoryAutoRecall = true),
        )
        try {
            val result = agent.buildSystemPromptWithRecall("Tell me about my preferences")
            assertNotNull(result)
            val (prompt, count) = result
            assertTrue(count > 0)
            assertTrue(prompt.contains("Context from previous conversations:"))
        } finally {
            agent.close()
        }
    }

    @Test
    fun buildSystemPromptWithRecall_withAutoRecallDisabled_returnsNull(): Unit = runBlocking {
        memoryManager.store("User likes coffee", "preference", "conversation")

        val agent = LumenAgent(
            config = LlmConfig(apiKey = "test"),
            memoryManager = memoryManager,
            userPreferences = UserPreferences(memoryAutoRecall = false),
        )
        try {
            val result = agent.buildSystemPromptWithRecall("Tell me about my preferences")
            assertNull(result)
        } finally {
            agent.close()
        }
    }

    @Test
    fun buildSystemPromptWithRecall_withNoMemoryManager_returnsNull(): Unit = runBlocking {
        val agent = LumenAgent(
            config = LlmConfig(apiKey = "test"),
            memoryManager = null,
            userPreferences = UserPreferences(memoryAutoRecall = true),
        )
        try {
            val result = agent.buildSystemPromptWithRecall("Tell me something")
            assertNull(result)
        } finally {
            agent.close()
        }
    }

    @Test
    fun buildSystemPromptWithRecall_withNoMatchingMemories_returnsNull(): Unit = runBlocking {
        // No memories stored — recall returns empty
        val agent = LumenAgent(
            config = LlmConfig(apiKey = "test"),
            memoryManager = memoryManager,
            userPreferences = UserPreferences(memoryAutoRecall = true),
        )
        try {
            val result = agent.buildSystemPromptWithRecall("Something random")
            assertNull(result)
        } finally {
            agent.close()
        }
    }

    @Test
    fun buildSystemPromptWithRecall_preservesPersonaPrompt(): Unit = runBlocking {
        memoryManager.store("User likes tea", "preference", "conversation")

        val agent = LumenAgent(
            config = LlmConfig(apiKey = "test"),
            memoryManager = memoryManager,
            userPreferences = UserPreferences(memoryAutoRecall = true),
            persona = com.lumen.core.database.entities.Persona(
                id = 1, name = "Custom", systemPrompt = "You are a custom assistant.",
            ),
        )
        try {
            val result = agent.buildSystemPromptWithRecall("What do I like?")
            assertNotNull(result)
            assertTrue(result.first.startsWith("You are a custom assistant."))
            assertTrue(result.first.contains("Context from previous conversations:"))
        } finally {
            agent.close()
        }
    }
}
