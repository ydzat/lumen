package com.lumen.server.dto

import com.lumen.core.config.AppConfig
import com.lumen.core.config.LlmConfig
import com.lumen.core.config.UserPreferences
import kotlinx.serialization.Serializable

private const val MASKED_KEY = "***"
private const val MASK_PREFIX_LENGTH = 3

@Serializable
data class SettingsDto(
    val llm: LlmConfigDto,
    val preferences: UserPreferences,
)

@Serializable
data class LlmConfigDto(
    val provider: String,
    val model: String,
    val apiKey: String,
    val apiBase: String,
)

@Serializable
data class SettingsUpdateRequest(
    val llm: LlmConfigDto,
    val preferences: UserPreferences,
)

fun AppConfig.toSettingsDto(): SettingsDto = SettingsDto(
    llm = LlmConfigDto(
        provider = llm.provider,
        model = llm.model,
        apiKey = maskApiKey(llm.apiKey),
        apiBase = llm.apiBase,
    ),
    preferences = preferences,
)

fun SettingsUpdateRequest.toAppConfig(existing: AppConfig): AppConfig = AppConfig(
    llm = LlmConfig(
        provider = llm.provider,
        model = llm.model,
        apiKey = if (isMaskedKey(llm.apiKey)) existing.llm.apiKey else llm.apiKey,
        apiBase = llm.apiBase,
    ),
    preferences = preferences,
)

internal fun maskApiKey(key: String): String {
    if (key.isBlank()) return ""
    if (key.length <= MASK_PREFIX_LENGTH) return MASKED_KEY
    return key.take(MASK_PREFIX_LENGTH) + MASKED_KEY
}

internal fun isMaskedKey(key: String): Boolean {
    return key.isBlank() || key == MASKED_KEY || key.endsWith(MASKED_KEY)
}
