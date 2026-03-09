package com.lumen.server.dto

import kotlinx.serialization.Serializable

@Serializable
data class ImportResponse(
    val status: String,
    val imported: Map<String, Int>,
)
