package com.lumen.core.config

import kotlinx.serialization.Serializable

@Serializable
data class LlmConfig(
    val provider: String = "deepseek",
    val model: String = "deepseek-chat",
    val apiKey: String = "",
    val apiBase: String = ""
) {
    fun resolveApiBase(): String {
        if (apiBase.isNotBlank()) return apiBase.trimEnd('/')

        return when (provider) {
            "deepseek" -> "https://api.deepseek.com"
            "openai" -> "https://api.openai.com"
            "anthropic" -> "https://api.anthropic.com"
            else -> throw IllegalArgumentException(
                "API base URL is required for provider '$provider'"
            )
        }
    }
}
