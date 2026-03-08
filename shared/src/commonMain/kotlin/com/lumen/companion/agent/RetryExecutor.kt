package com.lumen.companion.agent

import ai.koog.agents.core.tools.Tool
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import kotlinx.coroutines.delay

internal object RetryExecutor {

    private const val MAX_RETRIES = 3
    private val DELAYS_MS = longArrayOf(1000, 2000, 4000)

    suspend fun execute(
        client: LLMClient,
        prompt: Prompt,
        model: LLModel,
        tools: List<Tool<*, *>>,
    ): List<Message.Response> {
        val toolDescriptors = tools.map { it.descriptor }
        var lastException: Exception? = null

        for (attempt in 0 until MAX_RETRIES) {
            try {
                return client.execute(prompt, model, toolDescriptors)
            } catch (e: Exception) {
                if (!isRetryable(e)) throw e
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    delay(DELAYS_MS[attempt])
                }
            }
        }

        throw lastException!!
    }

    internal fun isRetryable(e: Exception): Boolean {
        val msg = e.message ?: return false
        if (msg.contains("429") || msg.contains("500") ||
            msg.contains("502") || msg.contains("503")
        ) return true

        val causeName = e.cause?.let { it::class.simpleName }
        return causeName in RETRYABLE_CAUSES
    }

    private val RETRYABLE_CAUSES = setOf(
        "ConnectException",
        "SocketTimeoutException",
        "UnknownHostException",
    )
}
