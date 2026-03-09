package com.lumen.core.database.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

@Entity
data class ResearchProject(
    @Id var id: Long = 0,
    @Index var name: String = "",
    var description: String = "",
    var keywords: String = "",
    @HnswIndex(dimensions = HNSW_DIMENSIONS)
    var embedding: FloatArray = floatArrayOf(),
    var isActive: Boolean = false,
    var createdAt: Long = 0,
    var updatedAt: Long = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ResearchProject) return false
        return id == other.id &&
            name == other.name &&
            description == other.description &&
            keywords == other.keywords &&
            embedding.contentEquals(other.embedding) &&
            isActive == other.isActive &&
            createdAt == other.createdAt &&
            updatedAt == other.updatedAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}
