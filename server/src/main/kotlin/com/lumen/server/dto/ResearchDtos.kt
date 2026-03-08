package com.lumen.server.dto

import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.Digest
import com.lumen.core.database.entities.ResearchProject
import com.lumen.core.database.entities.Source
import com.lumen.research.parseCsvSet
import kotlinx.serialization.Serializable

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
    val aiSummary: String,
    val aiRelevanceScore: Float,
    val keywords: String,
    val projectIds: List<Long>,
    val doi: String = "",
    val arxivId: String = "",
    val analysisStatus: String = "",
    val sourceType: String = "",
    val citationCount: Int = 0,
    val archived: Boolean = false,
)

@Serializable
data class ArticleListResponse(
    val articles: List<ArticleDto>,
    val total: Int,
    val page: Int,
    val size: Int,
)

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
    val config: String = "",
    val lastError: String = "",
    val consecutiveFailures: Int = 0,
    val nextRetryAt: Long = 0,
)

@Serializable
data class SourceCreateRequest(
    val name: String,
    val url: String,
    val type: String = "RSS",
    val category: String = "",
    val description: String = "",
    val icon: String = "",
    val refreshIntervalMin: Int = 60,
)

@Serializable
data class ProjectDto(
    val id: Long,
    val name: String,
    val description: String,
    val keywords: String,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class ProjectCreateRequest(
    val name: String,
    val description: String = "",
    val keywords: String = "",
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
    val sparks: String = "",
    val projectSections: String = "",
)

@Serializable
data class AnalyzeResponse(
    val fetched: Int,
    val embedded: Int = 0,
    val analyzed: Int,
    val scored: Int,
    val digestId: Long,
)

@Serializable
data class TrendsResponse(
    val days: Int,
    val digests: List<TrendsDigestEntry>,
)

@Serializable
data class TrendsDigestEntry(
    val date: String,
    val title: String,
    val content: String,
    val projectId: Long,
)

@Serializable
data class RefreshResponse(
    val fetched: Int,
)

fun Article.toDto(): ArticleDto = ArticleDto(
    id = id,
    sourceId = sourceId,
    title = title,
    url = url,
    summary = summary,
    content = content,
    author = author,
    publishedAt = publishedAt,
    fetchedAt = fetchedAt,
    readAt = readAt,
    starred = starred,
    aiSummary = aiSummary,
    aiRelevanceScore = aiRelevanceScore,
    keywords = keywords,
    projectIds = parseCsvSet(projectIds).mapNotNull { it.toLongOrNull() },
    doi = doi,
    arxivId = arxivId,
    analysisStatus = analysisStatus,
    sourceType = sourceType,
    citationCount = citationCount,
    archived = archived,
)

fun Source.toDto(): SourceDto = SourceDto(
    id = id,
    name = name,
    url = url,
    type = type,
    category = category,
    description = description,
    icon = icon,
    refreshIntervalMin = refreshIntervalMin,
    enabled = enabled,
    lastFetchedAt = lastFetchedAt,
    createdAt = createdAt,
    config = config,
    lastError = lastError,
    consecutiveFailures = consecutiveFailures,
    nextRetryAt = nextRetryAt,
)

fun ResearchProject.toDto(): ProjectDto = ProjectDto(
    id = id,
    name = name,
    description = description,
    keywords = keywords,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Digest.toDto(): DigestDto = DigestDto(
    id = id,
    date = date,
    title = title,
    content = content,
    sourceBreakdown = sourceBreakdown,
    projectId = projectId,
    createdAt = createdAt,
    sparks = sparks,
    projectSections = projectSections,
)

fun SourceCreateRequest.toEntity(): Source = Source(
    name = name,
    url = url,
    type = type,
    category = category,
    description = description,
    icon = icon,
    refreshIntervalMin = refreshIntervalMin,
)
