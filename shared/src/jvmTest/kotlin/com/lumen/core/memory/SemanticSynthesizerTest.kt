package com.lumen.core.memory

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.MemoryEntry
import com.lumen.core.database.entities.MyObjectBox
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class SemanticSynthesizerTest {

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "objectbox-test-${System.nanoTime()}")
        tempDir.mkdirs()
        val store = MyObjectBox.builder()
            .baseDirectory(tempDir)
            .build()
        db = LumenDatabase(store)
    }

    private val fakeEmbeddingClient = object : EmbeddingClient {
        override suspend fun embed(text: String): FloatArray {
            val seed = text.hashCode()
            return FloatArray(1536) { i -> ((seed + i) % 100) / 100f }
        }
        override suspend fun embedBatch(texts: List<String>): List<FloatArray> =
            texts.map { embed(it) }
    }

    @AfterTest
    fun teardown() {
        db.close()
        tempDir.deleteRecursively()
    }

    private fun makeEmbedding(seed: Float): FloatArray {
        return FloatArray(1536) { i -> (seed + i * 0.001f) }
    }

    private fun makeSimilarEmbedding(base: FloatArray, perturbation: Float = 0.001f): FloatArray {
        return FloatArray(base.size) { i -> base[i] + perturbation * (i % 3 - 1) }
    }

    private fun makeDifferentEmbedding(seed: Float): FloatArray {
        // Use sine with different frequencies to produce genuinely orthogonal vectors
        return FloatArray(1536) { i ->
            kotlin.math.sin(seed * 0.1f + i * seed * 0.01f).toFloat()
        }
    }

    @Test
    fun synthesize_emptyDatabase_returnsNoMatch() = runBlocking {
        val synthesizer = SemanticSynthesizer(db, LlmCall { _, _ -> "" }, fakeEmbeddingClient)
        val candidate = MemoryEntry(
            content = "User likes coffee",
            embedding = makeEmbedding(1f),
            createdAt = 1000L,
            updatedAt = 1000L,
        )

        val result = synthesizer.synthesize(candidate)

        assertTrue(result is SynthesisResult.NoMatch)
    }

    @Test
    fun synthesize_noSimilarEntries_returnsNoMatch() = runBlocking {
        val existing = MemoryEntry(
            content = "User studies mathematics",
            embedding = makeDifferentEmbedding(100f),
            createdAt = 1000L,
            updatedAt = 1000L,
        )
        db.memoryEntryBox.put(existing)

        val synthesizer = SemanticSynthesizer(db, LlmCall { _, _ -> "" }, fakeEmbeddingClient, similarityThreshold = 0.99f)
        val candidate = MemoryEntry(
            content = "User likes coffee",
            embedding = makeDifferentEmbedding(200f),
            createdAt = 2000L,
            updatedAt = 2000L,
        )

        val result = synthesizer.synthesize(candidate)

        assertTrue(result is SynthesisResult.NoMatch)
    }

    @Test
    fun synthesize_similarEntry_llmDecidesMerge_returnsMerged() = runBlocking {
        val baseEmbedding = makeEmbedding(1f)
        val existing = MemoryEntry(
            content = "User likes black coffee",
            category = "preference",
            source = "conversation",
            embedding = baseEmbedding,
            keywords = "coffee,black",
            importance = 0.7f,
            createdAt = 1000L,
            updatedAt = 1000L,
        )
        db.memoryEntryBox.put(existing)

        val fakeLlm = LlmCall { _, _ ->
            """
            {
                "action": "merge",
                "mergedContent": "User likes black coffee without milk",
                "reason": "Both entries describe the same coffee preference"
            }
            """.trimIndent()
        }
        val synthesizer = SemanticSynthesizer(db, fakeLlm, fakeEmbeddingClient, similarityThreshold = 0.5f)

        val candidate = MemoryEntry(
            content = "User prefers coffee without milk",
            category = "preference",
            source = "conversation",
            embedding = makeSimilarEmbedding(baseEmbedding),
            keywords = "coffee,milk",
            importance = 0.8f,
            createdAt = 2000L,
            updatedAt = 2000L,
        )

        val result = synthesizer.synthesize(candidate)

        assertTrue(result is SynthesisResult.Merged)
        val merged = (result as SynthesisResult.Merged).entry
        assertEquals("User likes black coffee without milk", merged.content)
        assertEquals(0.8f, merged.importance)
        assertEquals(1000L, merged.createdAt)
        assertTrue(merged.mergedFrom.contains(existing.id.toString()))
        assertTrue(merged.keywords.contains("coffee"))
        assertTrue(merged.keywords.contains("black"))
        assertTrue(merged.keywords.contains("milk"))

        // Old entry should be deleted
        assertNull(db.memoryEntryBox.get(existing.id))
        // Merged entry should be persisted
        assertNotEquals(0L, merged.id)
    }

    @Test
    fun synthesize_similarEntry_llmDecidesKeepBoth_returnsKept() = runBlocking {
        val baseEmbedding = makeEmbedding(1f)
        val existing = MemoryEntry(
            content = "User likes coffee",
            embedding = baseEmbedding,
            createdAt = 1000L,
            updatedAt = 1000L,
        )
        db.memoryEntryBox.put(existing)

        val fakeLlm = LlmCall { _, _ ->
            """{"action": "keep_both", "mergedContent": "", "reason": "Different facts"}"""
        }
        val synthesizer = SemanticSynthesizer(db, fakeLlm, fakeEmbeddingClient, similarityThreshold = 0.5f)

        val candidate = MemoryEntry(
            content = "User's office has a coffee machine",
            embedding = makeSimilarEmbedding(baseEmbedding),
            createdAt = 2000L,
            updatedAt = 2000L,
        )

        val result = synthesizer.synthesize(candidate)

        assertTrue(result is SynthesisResult.Kept)
        // Existing entry should still be there
        assertEquals(1, db.memoryEntryBox.all.size)
    }

    @Test
    fun synthesize_similarEntry_llmDecidesUpdate_returnsMerged() = runBlocking {
        val baseEmbedding = makeEmbedding(1f)
        val existing = MemoryEntry(
            content = "User lives in Berlin",
            embedding = baseEmbedding,
            createdAt = 1000L,
            updatedAt = 1000L,
        )
        db.memoryEntryBox.put(existing)

        val fakeLlm = LlmCall { _, _ ->
            """{"action": "update", "mergedContent": "User lives in Munich (moved from Berlin)", "reason": "New info supersedes old"}"""
        }
        val synthesizer = SemanticSynthesizer(db, fakeLlm, fakeEmbeddingClient, similarityThreshold = 0.5f)

        val candidate = MemoryEntry(
            content = "User moved to Munich",
            embedding = makeSimilarEmbedding(baseEmbedding),
            createdAt = 2000L,
            updatedAt = 2000L,
        )

        val result = synthesizer.synthesize(candidate)

        assertTrue(result is SynthesisResult.Merged)
        val merged = (result as SynthesisResult.Merged).entry
        assertEquals("User lives in Munich (moved from Berlin)", merged.content)
    }

    @Test
    fun synthesize_mergedFromChaining_preservesPreviousIds() = runBlocking {
        val baseEmbedding = makeEmbedding(1f)
        val existing = MemoryEntry(
            content = "User likes coffee",
            embedding = baseEmbedding,
            mergedFrom = "3,7",
            createdAt = 1000L,
            updatedAt = 1000L,
        )
        db.memoryEntryBox.put(existing)
        val existingId = existing.id

        val fakeLlm = LlmCall { _, _ ->
            """{"action": "merge", "mergedContent": "User enjoys coffee", "reason": "Same fact"}"""
        }
        val synthesizer = SemanticSynthesizer(db, fakeLlm, fakeEmbeddingClient, similarityThreshold = 0.5f)

        val candidate = MemoryEntry(
            content = "User enjoys coffee",
            embedding = makeSimilarEmbedding(baseEmbedding),
            createdAt = 2000L,
            updatedAt = 2000L,
        )

        val result = synthesizer.synthesize(candidate)

        assertTrue(result is SynthesisResult.Merged)
        val merged = (result as SynthesisResult.Merged).entry
        assertEquals("3,7,$existingId", merged.mergedFrom)
    }

    @Test
    fun synthesize_malformedLlmResponse_fallsBackToKept() = runBlocking {
        val baseEmbedding = makeEmbedding(1f)
        val existing = MemoryEntry(
            content = "User likes coffee",
            embedding = baseEmbedding,
            createdAt = 1000L,
            updatedAt = 1000L,
        )
        db.memoryEntryBox.put(existing)

        val fakeLlm = LlmCall { _, _ -> "This is not valid JSON at all" }
        val synthesizer = SemanticSynthesizer(db, fakeLlm, fakeEmbeddingClient, similarityThreshold = 0.5f)

        val candidate = MemoryEntry(
            content = "User enjoys coffee",
            embedding = makeSimilarEmbedding(baseEmbedding),
            createdAt = 2000L,
            updatedAt = 2000L,
        )

        val result = synthesizer.synthesize(candidate)

        assertTrue(result is SynthesisResult.Kept)
        // No data loss — existing entry still there
        assertEquals(1, db.memoryEntryBox.all.size)
    }

    @Test
    fun parseDecision_validJson_returnsDecision() {
        val synthesizer = SemanticSynthesizer(db, LlmCall { _, _ -> "" }, fakeEmbeddingClient)
        val decision = synthesizer.parseDecision(
            """{"action": "merge", "mergedContent": "combined", "reason": "same fact"}"""
        )
        assertEquals("merge", decision.action)
        assertEquals("combined", decision.mergedContent)
    }

    @Test
    fun parseDecision_jsonWrappedInMarkdown_returnsDecision() {
        val synthesizer = SemanticSynthesizer(db, LlmCall { _, _ -> "" }, fakeEmbeddingClient)
        val decision = synthesizer.parseDecision(
            """
            Here is my decision:
            ```json
            {"action": "keep_both", "mergedContent": "", "reason": "different"}
            ```
            """.trimIndent()
        )
        assertEquals("keep_both", decision.action)
    }
}
