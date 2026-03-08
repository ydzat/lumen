package com.lumen.research.collector

import kotlin.math.min
import kotlin.math.pow

object RetryPolicy {
    const val BASE_DELAY_MS: Long = 5 * 60 * 1000L // 5 minutes
    const val MAX_DELAY_MS: Long = 24 * 60 * 60 * 1000L // 24 hours

    fun computeNextRetryAt(
        nowMs: Long,
        consecutiveFailures: Int,
        baseDelayMs: Long = BASE_DELAY_MS,
        maxDelayMs: Long = MAX_DELAY_MS,
    ): Long {
        if (consecutiveFailures <= 0) return nowMs
        val exponent = (consecutiveFailures - 1).coerceAtMost(20)
        val delay = min(baseDelayMs * 2.0.pow(exponent).toLong(), maxDelayMs)
        return nowMs + delay
    }

    fun isRetryable(source: com.lumen.core.database.entities.Source, nowMs: Long): Boolean {
        return source.nextRetryAt <= nowMs
    }
}
