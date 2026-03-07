package com.lumen.core.memory

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.MemoryEntry
import com.lumen.core.database.entities.MemoryEntry_

class MemoryManager(
    private val database: LumenDatabase,
    private val embeddingClient: EmbeddingClient,
) {

    suspend fun store(content: String, category: String, source: String): MemoryEntry {
        val embedding = embeddingClient.embed(content)
        val now = System.currentTimeMillis()
        val entry = MemoryEntry(
            content = content,
            category = category,
            source = source,
            embedding = embedding,
            createdAt = now,
            updatedAt = now,
        )
        database.memoryEntryBox.put(entry)
        return entry
    }

    suspend fun recall(query: String, limit: Int = 5): List<MemoryEntry> {
        val queryEmbedding = embeddingClient.embed(query)
        val results = database.memoryEntryBox.query()
            .nearestNeighbors(MemoryEntry_.embedding, queryEmbedding, limit)
            .build()
            .find()

        val now = System.currentTimeMillis()
        for (entry in results) {
            entry.accessCount++
            entry.lastAccessedAt = now
        }
        if (results.isNotEmpty()) {
            database.memoryEntryBox.put(results)
        }

        return results
    }

    fun getById(id: Long): MemoryEntry? {
        return database.memoryEntryBox.get(id)
    }

    fun delete(id: Long) {
        database.memoryEntryBox.remove(id)
    }
}
