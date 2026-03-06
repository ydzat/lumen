package com.lumen.core.config

import kotlinx.serialization.Serializable

@Serializable
data class LlmConfig(
    val provider: String = "deepseek",
    val model: String = "deepseek-chat",
    val apiKey: String = "",
    val apiBase: String = ""
)
