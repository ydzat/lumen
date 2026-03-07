package com.lumen.core.document

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Document
import com.lumen.core.database.entities.DocumentChunk_
import com.lumen.core.database.entities.Document_

class DocumentManager(
    private val ingestionService: DocumentIngestionService,
    private val db: LumenDatabase,
) {

    suspend fun ingest(
        fileBytes: ByteArray,
        filename: String,
        mimeType: String,
        projectId: Long = 0,
    ): Document {
        val result = ingestionService.ingest(fileBytes, filename, mimeType, projectId)
        if (result.documentId == 0L) {
            throw IllegalArgumentException("Document is empty or could not be parsed")
        }
        return db.documentBox.get(result.documentId)
    }

    fun listByProject(projectId: Long): List<Document> {
        return db.documentBox.query()
            .equal(Document_.projectId, projectId)
            .build()
            .use { it.find() }
    }

    fun delete(documentId: Long) {
        val query = db.documentChunkBox.query()
            .equal(DocumentChunk_.documentId, documentId)
            .build()
        db.store.runInTx {
            query.use { it.remove() }
            db.documentBox.remove(documentId)
        }
    }

    fun getChunkCount(documentId: Long): Int {
        val document = db.documentBox.get(documentId)
        if (document == null || document.id == 0L) return 0
        return document.chunkCount
    }
}
