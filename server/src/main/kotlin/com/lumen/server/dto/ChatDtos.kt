package com.lumen.server.dto

import com.lumen.core.database.entities.Conversation
import com.lumen.core.database.entities.Message
import com.lumen.core.database.entities.Persona
import kotlinx.serialization.Serializable

@Serializable
data class ConversationDto(
    val id: Long,
    val title: String,
    val personaId: Long,
    val projectId: Long,
    val messageCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class ConversationCreateRequest(
    val title: String,
    val personaId: Long = 0,
    val projectId: Long = 0,
)

@Serializable
data class ConversationDetailDto(
    val conversation: ConversationDto,
    val messages: List<MessageDto>,
)

@Serializable
data class MessageDto(
    val id: Long,
    val conversationId: Long,
    val role: String,
    val content: String,
    val toolName: String,
    val toolArgs: String,
    val createdAt: Long,
)

@Serializable
data class SendMessageRequest(
    val content: String,
)

@Serializable
data class PersonaDto(
    val id: Long,
    val name: String,
    val systemPrompt: String,
    val greeting: String,
    val avatarEmoji: String,
    val isBuiltIn: Boolean,
    val isActive: Boolean,
    val createdAt: Long,
)

@Serializable
data class PersonaCreateRequest(
    val name: String,
    val systemPrompt: String,
    val greeting: String = "",
    val avatarEmoji: String = "",
)

fun Conversation.toDto(): ConversationDto = ConversationDto(
    id = id,
    title = title,
    personaId = personaId,
    projectId = projectId,
    messageCount = messageCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Message.toDto(): MessageDto = MessageDto(
    id = id,
    conversationId = conversationId,
    role = role,
    content = content,
    toolName = toolName,
    toolArgs = toolArgs,
    createdAt = createdAt,
)

fun Persona.toDto(): PersonaDto = PersonaDto(
    id = id,
    name = name,
    systemPrompt = systemPrompt,
    greeting = greeting,
    avatarEmoji = avatarEmoji,
    isBuiltIn = isBuiltIn,
    isActive = isActive,
    createdAt = createdAt,
)
