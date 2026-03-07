package com.lumen.core.document

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.DocumentChunk_
import com.lumen.core.database.entities.MyObjectBox
import com.lumen.core.memory.EmbeddingClient
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DocumentManagerTest {

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File
    private lateinit var manager: DocumentManager

    private val fakeEmbeddingClient = object : EmbeddingClient {
        override suspend fun embed(text: String): FloatArray {
            return FloatArray(384) { 0.1f }
        }

        override suspend fun embedBatch(texts: List<String>): List<FloatArray> {
            return texts.map { FloatArray(384) { 0.1f } }
        }
    }

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "objectbox-test-${System.nanoTime()}")
        tempDir.mkdirs()
        val store = MyObjectBox.builder()
            .baseDirectory(tempDir)
            .build()
        db = LumenDatabase(store)
        val ingestionService = DocumentIngestionService(
            parser = DocumentParser(),
            chunker = TextChunker(),
            embeddingClient = fakeEmbeddingClient,
            db = db,
        )
        manager = DocumentManager(ingestionService, db)
    }

    @AfterTest
    fun teardown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun ingest_returnsDocumentWithCorrectFields(): Unit = runBlocking {
        val text = "This is a test document with some content. ".repeat(10)
        val document = manager.ingest(
            fileBytes = text.toByteArray(),
            filename = "test.txt",
            mimeType = "text/plain",
        )

        assertNotEquals(0L, document.id)
        assertEquals("test.txt", document.filename)
        assertEquals("text/plain", document.mimeType)
        assertTrue(document.chunkCount > 0)
    }

    @Test
    fun ingest_emptyFile_throwsException(): Unit = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            manager.ingest(
                fileBytes = "".toByteArray(),
                filename = "empty.txt",
                mimeType = "text/plain",
            )
        }
    }

    @Test
    fun listByProject_returnsOnlyProjectDocuments(): Unit = runBlocking {
        val text = "Content for testing project filtering. ".repeat(10)
        manager.ingest(text.toByteArray(), "proj1.txt", "text/plain", projectId = 1)
        manager.ingest(text.toByteArray(), "proj2.txt", "text/plain", projectId = 2)
        manager.ingest(text.toByteArray(), "proj1b.txt", "text/plain", projectId = 1)

        val proj1Docs = manager.listByProject(1)
        val proj2Docs = manager.listByProject(2)

        assertEquals(2, proj1Docs.size)
        assertEquals(1, proj2Docs.size)
        assertTrue(proj1Docs.all { it.projectId == 1L })
        assertTrue(proj2Docs.all { it.projectId == 2L })
    }

    @Test
    fun delete_removesDocumentAndAllChunks(): Unit = runBlocking {
        val text = "Document content for deletion testing purposes. ".repeat(50)
        val document = manager.ingest(text.toByteArray(), "deleteme.txt", "text/plain")
        val docId = document.id
        assertTrue(document.chunkCount > 0)

        val chunksBefore = db.documentChunkBox.query()
            .equal(DocumentChunk_.documentId, docId)
            .build()
            .use { it.find() }
        assertTrue(chunksBefore.isNotEmpty())

        manager.delete(docId)

        // ObjectBox get() returns entity with id=0 when not found
        val deletedDoc = db.documentBox.get(docId)
        assertTrue(deletedDoc == null || deletedDoc.id == 0L, "Document should be removed")

        val chunksAfter = db.documentChunkBox.query()
            .equal(DocumentChunk_.documentId, docId)
            .build()
            .use { it.find() }
        assertTrue(chunksAfter.isEmpty(), "All chunks should be cascade deleted")
    }

    @Test
    fun getChunkCount_returnsCorrectCount(): Unit = runBlocking {
        val text = "Some document content for chunk counting. ".repeat(10)
        val document = manager.ingest(text.toByteArray(), "count.txt", "text/plain")

        assertEquals(document.chunkCount, manager.getChunkCount(document.id))
    }

    @Test
    fun getChunkCount_nonExistentDocument_returnsZero() {
        assertEquals(0, manager.getChunkCount(999999))
    }

    @Test
    fun ingest_withProjectId_setsProjectOnDocument(): Unit = runBlocking {
        val text = "Project-specific document content here. ".repeat(10)
        val document = manager.ingest(text.toByteArray(), "proj.txt", "text/plain", projectId = 42)

        assertEquals(42L, document.projectId)
    }
}
