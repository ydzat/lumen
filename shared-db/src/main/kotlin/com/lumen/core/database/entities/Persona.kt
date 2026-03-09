package com.lumen.core.database.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

@Entity
data class Persona(
    @Id var id: Long = 0,
    @Index var name: String = "",
    var systemPrompt: String = "",
    var greeting: String = "",
    var avatarEmoji: String = "",
    var isBuiltIn: Boolean = false,
    var isActive: Boolean = false,
    var createdAt: Long = 0,
)
