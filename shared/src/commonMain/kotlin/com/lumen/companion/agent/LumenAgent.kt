package com.lumen.companion.agent

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.params.LLMParams
import com.lumen.core.config.LlmConfig
import io.ktor.client.HttpClient

class LumenAgent(private val config: LlmConfig) {

    private val httpClient = HttpClient()
    private val llmClient: LLMClient = LlmClientFactory.createClient(config, httpClient)
    private val model = LlmClientFactory.resolveModel(config)

    suspend fun chat(message: String): ChatResult {
        if (config.apiKey.isBlank()) {
            return ChatResult.Error("API key is not configured")
        }

        val prompt = Prompt(
            listOf(Message.User(message, RequestMetaInfo.Empty)),
            "lumen-chat",
            LLMParams()
        )

        return try {
            val responses = llmClient.execute(prompt, model, emptyList())
            val responseText = responses.firstOrNull()?.content ?: ""
            ChatResult.Success(responseText)
        } catch (e: Exception) {
            val errorMessage = when {
                e.message?.contains("401") == true -> "Invalid API key"
                e.message?.contains("403") == true -> "Access denied"
                e.message?.contains("429") == true -> "Rate limited, please try again later"
                e.cause?.let { it::class.simpleName } == "UnknownHostException" -> "Network unavailable"
                e.cause?.let { it::class.simpleName } == "ConnectException" -> "Cannot connect to API server"
                else -> e.message ?: "Unknown error"
            }
            ChatResult.Error(errorMessage, e)
        }
    }

    fun close() {
        llmClient.close()
        httpClient.close()
    }
}
