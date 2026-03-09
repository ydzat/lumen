package com.lumen.core.memory

import com.lumen.core.database.entities.EMBEDDING_DIMENSIONS
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OnnxEmbeddingClientTest {

    private lateinit var client: OnnxEmbeddingClient

    private val modelAvailable: Boolean by lazy {
        javaClass.classLoader.getResourceAsStream("$MODEL_DIR/$MODEL_FILE") != null
    }

    @BeforeTest
    fun setup() {
        org.junit.Assume.assumeTrue("ONNX model not available, skipping", modelAvailable)
        val loader = ModelResourceLoader()
        client = OnnxEmbeddingClient(loader)
    }

    @Test
    fun embed_returnsCorrectDimension() = kotlinx.coroutines.runBlocking {
        val result = client.embed("Hello world")
        assertEquals(EMBEDDING_DIMENSIONS, result.size)
    }

    @Test
    fun embed_isNormalized() = kotlinx.coroutines.runBlocking {
        val result = client.embed("This is a test sentence for embedding normalization.")
        val norm = sqrt(result.map { it * it }.sum())
        assertTrue(abs(norm - 1.0f) < 0.01f, "Embedding should be L2 normalized, got norm=$norm")
    }

    @Test
    fun embedBatch_producesConsistentResults() = kotlinx.coroutines.runBlocking {
        val single = client.embed("Hello")
        val batch = client.embedBatch(listOf("Hello", "World"))
        // Batch padding may cause minor numerical differences; check cosine similarity
        val sim = cosineSimilarity(single, batch[0])
        assertTrue(sim > 0.95f, "Single and batch embeddings should be nearly identical, got sim=$sim")
    }

    @Test
    fun embed_similarTextsProduceHigherSimilarity() = kotlinx.coroutines.runBlocking {
        val e1 = client.embed("The cat sat on the mat")
        val e2 = client.embed("A cat was sitting on a mat")
        val e3 = client.embed("Quantum physics equations and formulas")

        val sim12 = cosineSimilarity(e1, e2)
        val sim13 = cosineSimilarity(e1, e3)

        assertTrue(sim12 > sim13, "Similar sentences should have higher similarity: sim12=$sim12 vs sim13=$sim13")
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        return dot / (sqrt(normA) * sqrt(normB))
    }
}
