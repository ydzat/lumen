package com.lumen.core.database.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

@Entity
data class Message(
    @Id var id: Long = 0,
    @Index var conversationId: Long = 0,
    var role: String = "",
    var content: String = "",
    var toolName: String = "",
    var toolArgs: String = "",
    @Index var createdAt: Long = 0,
)
