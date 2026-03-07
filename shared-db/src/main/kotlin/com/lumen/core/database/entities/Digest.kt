package com.lumen.core.database.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

@Entity
data class Digest(
    @Id var id: Long = 0,
    @Index var date: String = "",
    var title: String = "",
    var content: String = "",
    var sourceBreakdown: String = "",
    var projectId: Long = 0,
    @Index var createdAt: Long = 0
)
