package com.lumen.core.document

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Document
import com.lumen.core.database.entities.DocumentChunk
import com.lumen.core.memory.EmbeddingClient

class DocumentIngestionService(
    private val parser: DocumentParser,
    private val chunker: TextChunker,
    private val embeddingClient: EmbeddingClient,
    private val db: LumenDatabase,
) {

    suspend fun ingest(
        fileBytes: ByteArray,
        filename: String,
        mimeType: String,
        projectId: Long = 0,
    ): IngestionResult {
        val text = parser.extractText(fileBytes, mimeType)
        if (text.isBlank()) {
            return IngestionResult(documentId = 0, chunkCount = 0)
        }

        val chunks = chunker.chunk(text)

        val document = Document(
            filename = filename,
            mimeType = mimeType,
            textContent = text,
            chunkCount = chunks.size,
            projectId = projectId,
            createdAt = System.currentTimeMillis(),
        )
        val documentId = db.documentBox.put(document)

        for (batch in chunks.chunked(EMBEDDING_BATCH_SIZE)) {
            val texts = batch.map { it.content }
            val embeddings = embeddingClient.embedBatch(texts)

            val entities = batch.zip(embeddings) { chunk, embedding ->
                DocumentChunk(
                    documentId = documentId,
                    projectId = projectId,
                    chunkIndex = chunk.chunkIndex,
                    content = chunk.content,
                    embedding = embedding,
                )
            }
            db.documentChunkBox.put(entities)
        }

        return IngestionResult(documentId = documentId, chunkCount = chunks.size)
    }

    companion object {
        const val EMBEDDING_BATCH_SIZE = 32
    }
}

data class IngestionResult(
    val documentId: Long,
    val chunkCount: Int,
)
