package com.lumen.core.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CosineSimilarityTest {

    @Test
    fun cosineSimilarity_identicalVectors_returns1() {
        val v = floatArrayOf(1f, 2f, 3f)
        val result = cosineSimilarity(v, v)
        assertTrue(result > 0.999f, "Expected ~1.0 but got $result")
    }

    @Test
    fun cosineSimilarity_orthogonalVectors_returns0() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(0f, 1f, 0f)
        val result = cosineSimilarity(a, b)
        assertTrue(result < 0.001f && result > -0.001f, "Expected ~0.0 but got $result")
    }

    @Test
    fun cosineSimilarity_oppositeVectors_returnsNegative1() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(-1f, -2f, -3f)
        val result = cosineSimilarity(a, b)
        assertTrue(result < -0.999f, "Expected ~-1.0 but got $result")
    }

    @Test
    fun cosineSimilarity_zeroVector_returns0() {
        val a = floatArrayOf(0f, 0f, 0f)
        val b = floatArrayOf(1f, 2f, 3f)
        val result = cosineSimilarity(a, b)
        assertEquals(0f, result)
    }

    @Test
    fun cosineSimilarity_differentSizes_throwsException() {
        val a = floatArrayOf(1f, 2f)
        val b = floatArrayOf(1f, 2f, 3f)
        assertFailsWith<IllegalArgumentException> {
            cosineSimilarity(a, b)
        }
    }

    @Test
    fun cosineSimilarity_similarVectors_returnsHighValue() {
        val a = floatArrayOf(1f, 2f, 3f, 4f, 5f)
        val b = floatArrayOf(1.01f, 2.01f, 3.01f, 4.01f, 5.01f)
        val result = cosineSimilarity(a, b)
        assertTrue(result > 0.99f, "Expected high similarity but got $result")
    }
}
