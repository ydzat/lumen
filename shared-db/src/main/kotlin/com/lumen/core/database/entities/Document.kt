package com.lumen.core.database.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

@Entity
data class Document(
    @Id var id: Long = 0,
    @Index var projectId: Long = 0,
    var filename: String = "",
    var mimeType: String = "",
    var textContent: String = "",
    var chunkCount: Int = 0,
    @Index var createdAt: Long = 0,
)
