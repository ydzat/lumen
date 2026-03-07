package com.lumen.core.database.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

@Entity
data class Conversation(
    @Id var id: Long = 0,
    var title: String = "",
    var personaId: Long = 0,
    var projectId: Long = 0,
    var messageCount: Int = 0,
    @Index var createdAt: Long = 0,
    var updatedAt: Long = 0,
)
