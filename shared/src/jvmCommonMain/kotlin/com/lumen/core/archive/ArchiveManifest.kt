package com.lumen.core.archive

import kotlinx.serialization.Serializable

@Serializable
data class ArchiveManifest(
    val version: Int = 1,
    val createdAt: Long,
    val appVersion: String = "1.0.0",
    val counts: Map<String, Int>,
)
