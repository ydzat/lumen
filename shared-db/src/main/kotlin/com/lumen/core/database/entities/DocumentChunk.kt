package com.lumen.core.database.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

@Entity
data class DocumentChunk(
    @Id var id: Long = 0,
    @Index var documentId: Long = 0,
    var projectId: Long = 0,
    var chunkIndex: Int = 0,
    var content: String = "",
    @HnswIndex(dimensions = HNSW_DIMENSIONS)
    var embedding: FloatArray = floatArrayOf(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DocumentChunk) return false
        return id == other.id &&
            documentId == other.documentId &&
            projectId == other.projectId &&
            chunkIndex == other.chunkIndex &&
            content == other.content &&
            embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + documentId.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}
