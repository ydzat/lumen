package com.lumen.core.memory

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.EMBEDDING_DIMENSIONS
import com.lumen.core.database.entities.MyObjectBox
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class SemanticCompressorTest {

    @Test
    fun compress_withValidLlmResponse_extractsMemories() = runBlocking {
        val fakeLlm = LlmCall { _, _ ->
            """
            [
              {
                "content": "The user is a PhD student at RWTH Aachen",
                "category": "fact",
                "keywords": ["PhD", "RWTH Aachen", "student"],
                "importance": 0.9,
                "originalTimestamp": ""
              },
              {
                "content": "The user prefers dark mode in all applications",
                "category": "preference",
                "keywords": ["dark mode", "UI"],
                "importance": 0.6,
                "originalTimestamp": ""
              }
            ]
            """.trimIndent()
        }
        val compressor = SemanticCompressor(fakeLlm)

        val results = compressor.compress("Some conversation", System.currentTimeMillis())

        assertEquals(2, results.size)
        assertEquals("The user is a PhD student at RWTH Aachen", results[0].content)
        assertEquals("fact", results[0].category)
        assertEquals(0.9f, results[0].importance)
        assertEquals(listOf("PhD", "RWTH Aachen", "student"), results[0].keywords)
        assertEquals("preference", results[1].category)
    }

    @Test
    fun compress_withMalformedJson_returnsEmptyList() = runBlocking {
        val fakeLlm = LlmCall { _, _ -> "This is not valid JSON at all" }
        val compressor = SemanticCompressor(fakeLlm)

        val results = compressor.compress("Some conversation", System.currentTimeMillis())

        assertTrue(results.isEmpty())
    }

    @Test
    fun compress_withEmptyArray_returnsEmptyList() = runBlocking {
        val fakeLlm = LlmCall { _, _ -> "[]" }
        val compressor = SemanticCompressor(fakeLlm)

        val results = compressor.compress("Some conversation", System.currentTimeMillis())

        assertTrue(results.isEmpty())
    }

    @Test
    fun compress_withJsonWrappedInMarkdown_extractsMemories() = runBlocking {
        val fakeLlm = LlmCall { _, _ ->
            """
            Here are the extracted memories:
            ```json
            [
              {
                "content": "User likes coffee",
                "category": "preference",
                "keywords": ["coffee"],
                "importance": 0.5,
                "originalTimestamp": ""
              }
            ]
            ```
            """.trimIndent()
        }
        val compressor = SemanticCompressor(fakeLlm)

        val results = compressor.compress("Some conversation", System.currentTimeMillis())

        assertEquals(1, results.size)
        assertEquals("User likes coffee", results[0].content)
    }

    @Test
    fun compress_includesCurrentDateInPrompt() = runBlocking {
        var capturedSystem = ""
        val fakeLlm = LlmCall { system, _ ->
            capturedSystem = system
            "[]"
        }
        val compressor = SemanticCompressor(fakeLlm)
        // 2026-03-07 in millis (approximate)
        val march7 = 1772928000000L

        compressor.compress("Test", march7)

        assertTrue(capturedSystem.contains("2026-03-07"), "System prompt should contain current date")
    }

    @Test
    fun compress_withExtraFieldsInJson_ignoresUnknownFields() = runBlocking {
        val fakeLlm = LlmCall { _, _ ->
            """
            [
              {
                "content": "Test memory",
                "category": "fact",
                "keywords": [],
                "importance": 0.5,
                "originalTimestamp": "",
                "unknownField": "should be ignored"
              }
            ]
            """.trimIndent()
        }
        val compressor = SemanticCompressor(fakeLlm)

        val results = compressor.compress("Some conversation", System.currentTimeMillis())

        assertEquals(1, results.size)
        assertEquals("Test memory", results[0].content)
    }

    @Test
    fun storeFromConversation_storesAllExtractedMemories() = runBlocking {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "objectbox-test-${System.nanoTime()}")
        tempDir.mkdirs()
        val store = MyObjectBox.builder().baseDirectory(tempDir).build()
        val db = LumenDatabase(store)

        try {
            val fakeLlm = LlmCall { _, _ ->
                """
                [
                  {"content": "User studies AI", "category": "fact", "keywords": ["AI"], "importance": 0.8, "originalTimestamp": ""},
                  {"content": "User likes tea", "category": "preference", "keywords": ["tea"], "importance": 0.4, "originalTimestamp": ""}
                ]
                """.trimIndent()
            }
            val fakeEmbedding = object : EmbeddingClient {
                override suspend fun embed(text: String): FloatArray =
                    FloatArray(EMBEDDING_DIMENSIONS) { (text.hashCode() + it) % 100 / 100f }
                override suspend fun embedBatch(texts: List<String>): List<FloatArray> =
                    texts.map { embed(it) }
            }
            val compressor = SemanticCompressor(fakeLlm)
            val noopSynthesizer = SemanticSynthesizer(db, LlmCall { _, _ -> "" }, fakeEmbedding, similarityThreshold = 1.0f)
            val directRetriever = IntentRetriever(db, LlmCall { _, _ -> "" }, fakeEmbedding, shortQueryThreshold = Int.MAX_VALUE)
            val manager = MemoryManager(db, fakeEmbedding, compressor, noopSynthesizer, directRetriever)

            val entries = manager.storeFromConversation("User: I study AI and I like tea")

            assertEquals(2, entries.size)
            assertEquals("User studies AI", entries[0].content)
            assertEquals("conversation", entries[0].source)
            assertEquals("fact", entries[0].category)
            assertEquals(0.8f, entries[0].importance)
            assertEquals("User likes tea", entries[1].content)
            assertEquals("preference", entries[1].category)
            assertNotEquals(0L, entries[0].id)
            assertNotEquals(0L, entries[1].id)

            // Verify persisted in database
            assertEquals(2, db.memoryEntryBox.all.size)
        } finally {
            db.close()
            tempDir.deleteRecursively()
        }
    }
}
