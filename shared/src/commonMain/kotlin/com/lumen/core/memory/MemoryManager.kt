package com.lumen.core.memory

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.MemoryEntry
import com.lumen.core.database.entities.MemoryEntry_

class MemoryManager(
    private val database: LumenDatabase,
    private val embeddingClient: EmbeddingClient,
    private val compressor: SemanticCompressor,
    private val synthesizer: SemanticSynthesizer,
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
        return persistWithSynthesis(entry)
    }

    suspend fun recall(query: String, limit: Int = 5): List<MemoryEntry> {
        require(limit > 0) { "limit must be positive, got $limit" }
        val queryEmbedding = embeddingClient.embed(query)
        val results = database.memoryEntryBox.query()
            .nearestNeighbors(MemoryEntry_.embedding, queryEmbedding, limit)
            .build()
            .use { it.find() }

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

    suspend fun storeFromConversation(conversation: String): List<MemoryEntry> {
        val extracted = compressor.compress(conversation, System.currentTimeMillis())
        return extracted.map { memory ->
            val embedding = embeddingClient.embed(memory.content)
            val now = System.currentTimeMillis()
            val entry = MemoryEntry(
                content = memory.content,
                category = memory.category,
                source = "conversation",
                embedding = embedding,
                keywords = memory.keywords.joinToString(","),
                importance = memory.importance,
                originalTimestamp = memory.originalTimestamp,
                createdAt = now,
                updatedAt = now,
            )
            persistWithSynthesis(entry)
        }
    }

    private suspend fun persistWithSynthesis(entry: MemoryEntry): MemoryEntry {
        val result = synthesizer.synthesize(entry)
        return when (result) {
            is SynthesisResult.NoMatch, is SynthesisResult.Kept -> {
                database.memoryEntryBox.put(entry)
                entry
            }
            is SynthesisResult.Merged -> result.entry
        }
    }

    fun getById(id: Long): MemoryEntry? {
        return database.memoryEntryBox.get(id)
    }

    fun delete(id: Long) {
        database.memoryEntryBox.remove(id)
    }
}
