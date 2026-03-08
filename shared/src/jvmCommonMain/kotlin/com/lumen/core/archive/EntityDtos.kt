package com.lumen.core.archive

import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.Conversation
import com.lumen.core.database.entities.Digest
import com.lumen.core.database.entities.Document
import com.lumen.core.database.entities.DocumentChunk
import com.lumen.core.database.entities.MemoryEntry
import com.lumen.core.database.entities.Message
import com.lumen.core.database.entities.Persona
import com.lumen.core.database.entities.ResearchProject
import com.lumen.core.database.entities.Source
import kotlinx.serialization.Serializable

@Serializable
data class SourceDto(
    val id: Long,
    val name: String,
    val url: String,
    val type: String,
    val category: String,
    val description: String,
    val icon: String,
    val refreshIntervalMin: Int,
    val enabled: Boolean,
    val lastFetchedAt: Long,
    val createdAt: Long,
)

@Serializable
data class ArticleDto(
    val id: Long,
    val sourceId: Long,
    val title: String,
    val url: String,
    val summary: String,
    val content: String,
    val author: String,
    val publishedAt: Long,
    val fetchedAt: Long,
    val readAt: Long,
    val starred: Boolean,
    val embedding: List<Float>,
    val aiSummary: String,
    val aiRelevanceScore: Float,
    val keywords: String,
    val projectIds: String,
)

@Serializable
data class DigestDto(
    val id: Long,
    val date: String,
    val title: String,
    val content: String,
    val sourceBreakdown: String,
    val projectId: Long,
    val createdAt: Long,
)

@Serializable
data class ResearchProjectDto(
    val id: Long,
    val name: String,
    val description: String,
    val keywords: String,
    val embedding: List<Float>,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

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
data class MessageDto(
    val id: Long,
    val conversationId: Long,
    val role: String,
    val content: String,
    val toolName: String,
    val toolArgs: String,
    val toolCallId: String = "",
    val createdAt: Long,
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
data class DocumentDto(
    val id: Long,
    val projectId: Long,
    val filename: String,
    val mimeType: String,
    val textContent: String,
    val chunkCount: Int,
    val createdAt: Long,
)

@Serializable
data class DocumentChunkDto(
    val id: Long,
    val documentId: Long,
    val projectId: Long,
    val chunkIndex: Int,
    val content: String,
    val embedding: List<Float>,
)

@Serializable
data class MemoryEntryDto(
    val id: Long,
    val content: String,
    val category: String,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
    val originalTimestamp: String,
    val embedding: List<Float>,
    val keywords: String,
    val importance: Float,
    val accessCount: Int,
    val lastAccessedAt: Long,
    val mergedFrom: String,
)

// --- Conversion extensions ---

fun Source.toDto() = SourceDto(
    id, name, url, type, category, description, icon,
    refreshIntervalMin, enabled, lastFetchedAt, createdAt,
)

fun SourceDto.toEntity() = Source(
    0, name, url, type, category, description, icon,
    refreshIntervalMin, enabled, lastFetchedAt, createdAt,
)

fun Article.toDto() = ArticleDto(
    id, sourceId, title, url, summary, content, author,
    publishedAt, fetchedAt, readAt, starred, embedding.toList(),
    aiSummary, aiRelevanceScore, keywords, projectIds,
)

fun ArticleDto.toEntity(sourceId: Long = this.sourceId, projectIds: String = this.projectIds) = Article(
    0, sourceId, title, url, summary, content, author,
    publishedAt, fetchedAt, readAt, starred, embedding.toFloatArray(),
    aiSummary, aiRelevanceScore, keywords, projectIds,
)

fun Digest.toDto() = DigestDto(id, date, title, content, sourceBreakdown, projectId, createdAt)

fun DigestDto.toEntity(projectId: Long = this.projectId) = Digest(
    0, date, title, content, sourceBreakdown, projectId, createdAt,
)

fun ResearchProject.toDto() = ResearchProjectDto(
    id, name, description, keywords, embedding.toList(), isActive, createdAt, updatedAt,
)

fun ResearchProjectDto.toEntity() = ResearchProject(
    0, name, description, keywords, embedding.toFloatArray(), isActive, createdAt, updatedAt,
)

fun Conversation.toDto() = ConversationDto(id, title, personaId, projectId, messageCount, createdAt, updatedAt)

fun ConversationDto.toEntity(personaId: Long = this.personaId, projectId: Long = this.projectId) = Conversation(
    0, title, personaId, projectId, messageCount, createdAt, updatedAt,
)

fun Message.toDto() = MessageDto(id, conversationId, role, content, toolName, toolArgs, toolCallId, createdAt)

fun MessageDto.toEntity(conversationId: Long = this.conversationId) = Message(
    0, conversationId, role, content, toolName, toolArgs, toolCallId, createdAt,
)

fun Persona.toDto() = PersonaDto(id, name, systemPrompt, greeting, avatarEmoji, isBuiltIn, isActive, createdAt)

fun PersonaDto.toEntity() = Persona(0, name, systemPrompt, greeting, avatarEmoji, isBuiltIn, isActive, createdAt)

fun Document.toDto() = DocumentDto(id, projectId, filename, mimeType, textContent, chunkCount, createdAt)

fun DocumentDto.toEntity(projectId: Long = this.projectId) = Document(
    0, projectId, filename, mimeType, textContent, chunkCount, createdAt,
)

fun DocumentChunk.toDto() = DocumentChunkDto(
    id, documentId, projectId, chunkIndex, content, embedding.toList(),
)

fun DocumentChunkDto.toEntity(documentId: Long = this.documentId, projectId: Long = this.projectId) = DocumentChunk(
    0, documentId, projectId, chunkIndex, content, embedding.toFloatArray(),
)

fun MemoryEntry.toDto() = MemoryEntryDto(
    id, content, category, source, createdAt, updatedAt, originalTimestamp,
    embedding.toList(), keywords, importance, accessCount, lastAccessedAt, mergedFrom,
)

fun MemoryEntryDto.toEntity() = MemoryEntry(
    0, content, category, source, createdAt, updatedAt, originalTimestamp,
    embedding.toFloatArray(), keywords, importance, accessCount, lastAccessedAt, mergedFrom,
)
