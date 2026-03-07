package com.lumen.research.collector

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

actual class PlatformScheduler(private val collectorManager: CollectorManager) {

    private var scope: CoroutineScope? = null

    actual fun start(intervalMillis: Long) {
        stop()
        val newScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope = newScope
        newScope.launch {
            while (isActive) {
                try {
                    collectorManager.runPipeline()
                } catch (_: Exception) {
                    // Pipeline errors are non-fatal; retry next cycle
                }
                delay(intervalMillis)
            }
        }
    }

    actual fun stop() {
        scope?.cancel()
        scope = null
    }

    actual companion object {
        actual val DEFAULT_INTERVAL: Long = 4 * 60 * 60 * 1000L
    }
}
