package com.lumen.core.config

import kotlinx.serialization.Serializable

@Serializable
data class UserPreferences(
    val language: String = "zh",
    val theme: String = "system"
)
