package com.lumen.core.memory

import com.lumen.core.database.entities.EMBEDDING_DIMENSIONS
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmbeddingPostProcessingTest {

    @Test
    fun meanPoolAndNormalize_withUniformMask_computesCorrectMean() {
        val seqLen = 3
        val tokenEmbeddings = arrayOf(
            FloatArray(EMBEDDING_DIMENSIONS) { 1.0f },
            FloatArray(EMBEDDING_DIMENSIONS) { 2.0f },
            FloatArray(EMBEDDING_DIMENSIONS) { 3.0f },
        )
        val mask = longArrayOf(1, 1, 1)

        val result = OnnxEmbeddingClient.meanPoolAndNormalize(tokenEmbeddings, mask, seqLen)

        assertEquals(EMBEDDING_DIMENSIONS, result.size)
        // Mean of [1, 2, 3] = 2.0, then L2 normalized
        val expectedNorm = 1.0f
        val norm = sqrt(result.map { it * it }.sum())
        assertTrue(abs(norm - expectedNorm) < 0.001f, "Output should be L2 normalized, got norm=$norm")
    }

    @Test
    fun meanPoolAndNormalize_withPartialMask_ignoresPadding() {
        val seqLen = 2
        // Token 0: value concentrated in first half of dimensions
        val token0 = FloatArray(EMBEDDING_DIMENSIONS) { i -> if (i < EMBEDDING_DIMENSIONS / 2) 1.0f else 0.0f }
        // Token 1: value concentrated in second half (orthogonal direction)
        val token1 = FloatArray(EMBEDDING_DIMENSIONS) { i -> if (i >= EMBEDDING_DIMENSIONS / 2) 1.0f else 0.0f }
        val tokenEmbeddings = arrayOf(token0, token1)

        val bothMask = longArrayOf(1, 1)
        val firstOnly = longArrayOf(1, 0)

        val resultBoth = OnnxEmbeddingClient.meanPoolAndNormalize(tokenEmbeddings, bothMask, seqLen)
        val resultFirst = OnnxEmbeddingClient.meanPoolAndNormalize(tokenEmbeddings, firstOnly, seqLen)

        // With both tokens, mean is [0.5, 0.5] direction (diagonal)
        // With only first token, result is [1.0, 0.0] direction (axis-aligned)
        // These should be different directions
        val dot = resultBoth.zip(resultFirst.toTypedArray()).sumOf { (a, b) -> (a * b).toDouble() }
        assertTrue(dot < 0.95, "Partial mask should produce different direction, got dot=$dot")
    }

    @Test
    fun meanPoolAndNormalize_producesUnitVector() {
        val seqLen = 2
        val tokenEmbeddings = arrayOf(
            FloatArray(EMBEDDING_DIMENSIONS) { i -> (i + 1).toFloat() },
            FloatArray(EMBEDDING_DIMENSIONS) { i -> (i * 2).toFloat() },
        )
        val mask = longArrayOf(1, 1)

        val result = OnnxEmbeddingClient.meanPoolAndNormalize(tokenEmbeddings, mask, seqLen)

        val norm = sqrt(result.map { it * it }.sum())
        assertTrue(abs(norm - 1.0f) < 0.001f, "Output should be unit vector, got norm=$norm")
    }

    @Test
    fun meanPoolAndNormalize_withZeroMask_returnsZeroVector() {
        val seqLen = 2
        val tokenEmbeddings = arrayOf(
            FloatArray(EMBEDDING_DIMENSIONS) { 5.0f },
            FloatArray(EMBEDDING_DIMENSIONS) { 10.0f },
        )
        val mask = longArrayOf(0, 0)

        val result = OnnxEmbeddingClient.meanPoolAndNormalize(tokenEmbeddings, mask, seqLen)

        assertTrue(result.all { it == 0.0f }, "Zero mask should produce zero vector")
    }

    @Test
    fun meanPoolAndNormalize_outputDimensionMatchesConstant() {
        val seqLen = 1
        val tokenEmbeddings = arrayOf(FloatArray(EMBEDDING_DIMENSIONS) { 1.0f })
        val mask = longArrayOf(1)

        val result = OnnxEmbeddingClient.meanPoolAndNormalize(tokenEmbeddings, mask, seqLen)

        assertEquals(EMBEDDING_DIMENSIONS, result.size)
    }
}
