package com.lumen.core.database.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

@Entity
data class Article(
    @Id var id: Long = 0,
    var sourceId: Long = 0,
    @Index var title: String = "",
    @Index var url: String = "",
    var summary: String = "",
    var content: String = "",
    var author: String = "",
    var publishedAt: Long = 0,
    @Index var fetchedAt: Long = 0,
    var readAt: Long = 0,
    var starred: Boolean = false,
    @HnswIndex(dimensions = HNSW_DIMENSIONS)
    var embedding: FloatArray = floatArrayOf(),
    var aiSummary: String = "",
    var aiRelevanceScore: Float = 0f,
    var keywords: String = "",
    var projectIds: String = "",
    @Index var doi: String = "",
    @Index var arxivId: String = "",
    @Index var analysisStatus: String = "",
    var aiTranslation: String = "",
    var citationCount: Int = 0,
    var influentialCitationCount: Int = 0,
    var archived: Boolean = false,
    var sourceType: String = "",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Article) return false
        return id == other.id &&
            sourceId == other.sourceId &&
            title == other.title &&
            url == other.url &&
            summary == other.summary &&
            content == other.content &&
            author == other.author &&
            publishedAt == other.publishedAt &&
            fetchedAt == other.fetchedAt &&
            readAt == other.readAt &&
            starred == other.starred &&
            embedding.contentEquals(other.embedding) &&
            aiSummary == other.aiSummary &&
            aiRelevanceScore == other.aiRelevanceScore &&
            keywords == other.keywords &&
            projectIds == other.projectIds &&
            doi == other.doi &&
            arxivId == other.arxivId &&
            analysisStatus == other.analysisStatus &&
            aiTranslation == other.aiTranslation &&
            citationCount == other.citationCount &&
            influentialCitationCount == other.influentialCitationCount &&
            archived == other.archived &&
            sourceType == other.sourceType
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + doi.hashCode()
        result = 31 * result + arxivId.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}
