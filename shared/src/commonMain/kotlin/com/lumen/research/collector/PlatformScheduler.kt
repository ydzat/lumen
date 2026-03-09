package com.lumen.research.collector

expect class PlatformScheduler {
    fun start(intervalMillis: Long = DEFAULT_INTERVAL)
    fun stop()

    companion object {
        val DEFAULT_INTERVAL: Long
    }
}
