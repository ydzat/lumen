package com.lumen.core.database.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id

@Entity
data class MemoryEntry(
    @Id var id: Long = 0,
    var content: String = "",
    var category: String = "",
    var source: String = "",
    var createdAt: Long = 0,
    var updatedAt: Long = 0,
    var originalTimestamp: String = "",
    @HnswIndex(dimensions = 1536)
    var embedding: FloatArray = floatArrayOf(),
    var keywords: String = "",
    var importance: Float = 0f,
    var accessCount: Int = 0,
    var lastAccessedAt: Long = 0,
    var mergedFrom: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemoryEntry) return false
        return id == other.id &&
            content == other.content &&
            category == other.category &&
            source == other.source &&
            createdAt == other.createdAt &&
            updatedAt == other.updatedAt &&
            originalTimestamp == other.originalTimestamp &&
            embedding.contentEquals(other.embedding) &&
            keywords == other.keywords &&
            importance == other.importance &&
            accessCount == other.accessCount &&
            lastAccessedAt == other.lastAccessedAt &&
            mergedFrom == other.mergedFrom
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}
