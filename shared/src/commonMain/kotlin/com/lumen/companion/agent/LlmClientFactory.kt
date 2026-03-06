package com.lumen.companion.agent

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import com.lumen.core.config.LlmConfig
import io.ktor.client.HttpClient

object LlmClientFactory {

    fun createClient(config: LlmConfig, httpClient: HttpClient): LLMClient {
        return when (config.provider) {
            "deepseek" -> DeepSeekLLMClient(
                config.apiKey,
                DeepSeekClientSettings(config.apiBase.ifEmpty { "https://api.deepseek.com" }),
                httpClient
            )
            "openai" -> OpenAILLMClient(
                config.apiKey,
                OpenAIClientSettings(config.apiBase.ifEmpty { "https://api.openai.com" }),
                httpClient
            )
            "anthropic" -> AnthropicLLMClient(
                config.apiKey,
                AnthropicClientSettings(),
                httpClient
            )
            else -> {
                require(config.apiBase.isNotBlank()) { "apiBase is required for custom provider '${config.provider}'" }
                OpenAILLMClient(
                    config.apiKey,
                    OpenAIClientSettings(config.apiBase),
                    httpClient
                )
            }
        }
    }

    fun resolveModel(config: LlmConfig): LLModel {
        val provider = when (config.provider) {
            "deepseek" -> LLMProvider.DeepSeek
            "openai" -> LLMProvider.OpenAI
            "anthropic" -> LLMProvider.Anthropic
            else -> LLMProvider.OpenAI
        }
        return LLModel(provider, config.model, listOf(LLMCapability.Completion))
    }
}
