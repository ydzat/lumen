package com.lumen.core.config

import kotlinx.serialization.Serializable

@Serializable
data class UserPreferences(
    val language: String = "zh",
    val theme: String = "system",
    val memoryAutoRecall: Boolean = true,
    val memoryExtractionInterval: Int = 10,
    val hasCompletedOnboarding: Boolean = false,
)
