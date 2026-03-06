package com.lumen.core.config

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val llm: LlmConfig = LlmConfig(),
    val preferences: UserPreferences = UserPreferences()
)
