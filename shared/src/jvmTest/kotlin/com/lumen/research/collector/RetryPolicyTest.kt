package com.lumen.research.collector

import com.lumen.core.database.entities.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetryPolicyTest {

    @Test
    fun computeNextRetryAt_firstFailure_usesBaseDelay() {
        val now = 1000000L
        val nextRetry = RetryPolicy.computeNextRetryAt(now, 1)

        assertEquals(now + RetryPolicy.BASE_DELAY_MS, nextRetry)
    }

    @Test
    fun computeNextRetryAt_secondFailure_doublesDelay() {
        val now = 1000000L
        val nextRetry = RetryPolicy.computeNextRetryAt(now, 2)

        assertEquals(now + RetryPolicy.BASE_DELAY_MS * 2, nextRetry)
    }

    @Test
    fun computeNextRetryAt_thirdFailure_quadruplesDelay() {
        val now = 1000000L
        val nextRetry = RetryPolicy.computeNextRetryAt(now, 3)

        assertEquals(now + RetryPolicy.BASE_DELAY_MS * 4, nextRetry)
    }

    @Test
    fun computeNextRetryAt_cappedAtMaxDelay() {
        val now = 1000000L
        val nextRetry = RetryPolicy.computeNextRetryAt(now, 100)

        assertEquals(now + RetryPolicy.MAX_DELAY_MS, nextRetry)
    }

    @Test
    fun computeNextRetryAt_zeroFailures_returnsNow() {
        val now = 1000000L
        val nextRetry = RetryPolicy.computeNextRetryAt(now, 0)

        assertEquals(now, nextRetry)
    }

    @Test
    fun isRetryable_noNextRetry_returnsTrue() {
        val source = Source(name = "Test", url = "https://example.com", nextRetryAt = 0)

        assertTrue(RetryPolicy.isRetryable(source, System.currentTimeMillis()))
    }

    @Test
    fun isRetryable_retryTimeInPast_returnsTrue() {
        val source = Source(name = "Test", url = "https://example.com", nextRetryAt = 1000L)

        assertTrue(RetryPolicy.isRetryable(source, 2000L))
    }

    @Test
    fun isRetryable_retryTimeInFuture_returnsFalse() {
        val now = System.currentTimeMillis()
        val source = Source(name = "Test", url = "https://example.com", nextRetryAt = now + 60000)

        assertFalse(RetryPolicy.isRetryable(source, now))
    }

    @Test
    fun computeNextRetryAt_customBaseAndMax() {
        val now = 0L
        val nextRetry = RetryPolicy.computeNextRetryAt(
            now,
            consecutiveFailures = 3,
            baseDelayMs = 1000,
            maxDelayMs = 5000,
        )

        assertEquals(4000L, nextRetry) // 1000 * 2^2 = 4000
    }

    @Test
    fun computeNextRetryAt_customMax_capsCorrectly() {
        val now = 0L
        val nextRetry = RetryPolicy.computeNextRetryAt(
            now,
            consecutiveFailures = 10,
            baseDelayMs = 1000,
            maxDelayMs = 5000,
        )

        assertEquals(5000L, nextRetry) // capped at max
    }
}
