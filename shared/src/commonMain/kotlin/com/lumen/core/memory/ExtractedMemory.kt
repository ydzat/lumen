package com.lumen.core.memory

import kotlinx.serialization.Serializable

@Serializable
data class ExtractedMemory(
    val content: String,
    val category: String = "general",
    val keywords: List<String> = emptyList(),
    val importance: Float = 0.5f,
    val originalTimestamp: String = "",
)
