package com.lumen.core.memory

import kotlinx.serialization.Serializable

@Serializable
data class MergeDecision(
    val action: String,
    val mergedContent: String = "",
    val reason: String = "",
)
