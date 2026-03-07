package com.lumen.server.dto

import com.lumen.core.database.entities.Document
import kotlinx.serialization.Serializable

@Serializable
data class DocumentDto(
    val id: Long,
    val projectId: Long,
    val filename: String,
    val mimeType: String,
    val chunkCount: Int,
    val createdAt: Long,
)

fun Document.toDto(): DocumentDto = DocumentDto(
    id = id,
    projectId = projectId,
    filename = filename,
    mimeType = mimeType,
    chunkCount = chunkCount,
    createdAt = createdAt,
)
