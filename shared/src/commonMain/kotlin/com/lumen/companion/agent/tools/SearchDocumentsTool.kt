package com.lumen.companion.agent.tools

import ai.koog.agents.core.tools.SimpleTool
import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.DocumentChunk_
import com.lumen.core.memory.EmbeddingClient
import kotlinx.serialization.Serializable

@Serializable
data class SearchDocumentsArgs(
    val query: String,
    val projectId: Long = 0,
    val limit: Int = 5,
)

class SearchDocumentsTool(
    private val db: LumenDatabase,
    private val embeddingClient: EmbeddingClient,
    private val defaultProjectId: Long = 0,
) : SimpleTool<SearchDocumentsArgs>(
    SearchDocumentsArgs.serializer(),
    "search_documents",
    "Search uploaded documents by semantic similarity to a query. Optionally scope to a project.",
) {
    override suspend fun execute(args: SearchDocumentsArgs): String {
        val effectiveProjectId = args.projectId.takeIf { it > 0 } ?: defaultProjectId
        val embedding = embeddingClient.embed(args.query)

        val searchLimit = if (effectiveProjectId > 0) args.limit * OVER_FETCH_FACTOR else args.limit
        val allChunks = db.documentChunkBox.query()
            .nearestNeighbors(DocumentChunk_.embedding, embedding, searchLimit)
            .build()
            .use { it.find() }

        val chunks = if (effectiveProjectId > 0) {
            allChunks.filter { it.projectId == effectiveProjectId }.take(args.limit)
        } else {
            allChunks
        }

        if (chunks.isEmpty()) return "No matching documents found."

        val documentIds = chunks.map { it.documentId }.distinct()
        val documents = documentIds.associateWith { db.documentBox.get(it) }

        return chunks.joinToString("\n\n") { chunk ->
            val doc = documents[chunk.documentId]
            val filename = doc?.filename ?: "unknown"
            "- [${filename} #${chunk.chunkIndex}]\n  ${chunk.content.take(MAX_CONTENT_LENGTH)}"
        }
    }

    companion object {
        private const val MAX_CONTENT_LENGTH = 500
        private const val OVER_FETCH_FACTOR = 3
    }
}
