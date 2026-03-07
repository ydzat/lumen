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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DocumentIngestionServiceTest {

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File
    private lateinit var service: DocumentIngestionService

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
        service = DocumentIngestionService(
            parser = DocumentParser(),
            chunker = TextChunker(),
            embeddingClient = fakeEmbeddingClient,
            db = db,
        )
    }

    @AfterTest
    fun teardown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun ingest_plainText_persistsDocumentAndChunks(): Unit = runBlocking {
        val text = "This is a test document with some content. ".repeat(50)
        val result = service.ingest(
            fileBytes = text.toByteArray(),
            filename = "test.txt",
            mimeType = "text/plain",
        )

        assertNotEquals(0L, result.documentId)
        assertTrue(result.chunkCount > 0)

        val document = db.documentBox.get(result.documentId)
        assertEquals("test.txt", document.filename)
        assertEquals("text/plain", document.mimeType)
        assertEquals(result.chunkCount, document.chunkCount)
        assertTrue(document.textContent.isNotBlank())

        val query = db.documentChunkBox.query()
            .equal(DocumentChunk_.documentId, result.documentId)
            .build()
        val chunks = query.find()
        query.close()
        assertEquals(result.chunkCount, chunks.size)

        for (chunk in chunks) {
            assertTrue(chunk.content.isNotBlank())
            assertEquals(384, chunk.embedding.size)
        }
    }

    @Test
    fun ingest_emptyText_returnsZeroChunks(): Unit = runBlocking {
        val result = service.ingest(
            fileBytes = "".toByteArray(),
            filename = "empty.txt",
            mimeType = "text/plain",
        )

        assertEquals(0L, result.documentId)
        assertEquals(0, result.chunkCount)
    }

    @Test
    fun ingest_withProjectId_setsProjectOnEntities(): Unit = runBlocking {
        val text = "Project-specific document content for testing. ".repeat(10)
        val result = service.ingest(
            fileBytes = text.toByteArray(),
            filename = "project.txt",
            mimeType = "text/plain",
            projectId = 42,
        )

        val document = db.documentBox.get(result.documentId)
        assertEquals(42L, document.projectId)

        val query = db.documentChunkBox.query()
            .equal(DocumentChunk_.documentId, result.documentId)
            .build()
        val chunks = query.find()
        query.close()
        for (chunk in chunks) {
            assertEquals(42L, chunk.projectId)
        }
    }

    @Test
    fun ingest_markdown_parsesCorrectly(): Unit = runBlocking {
        val markdown = "# Heading\n\nSome paragraph text. Another sentence here.\n\n## Subheading\n\nMore content."
        val result = service.ingest(
            fileBytes = markdown.toByteArray(),
            filename = "notes.md",
            mimeType = "text/markdown",
        )

        assertNotEquals(0L, result.documentId)
        val document = db.documentBox.get(result.documentId)
        assertTrue(document.textContent.contains("# Heading"))
    }

    @Test
    fun ingest_largeBatch_processesInBatches(): Unit = runBlocking {
        // Create text large enough to produce > 32 chunks
        // ~13 words per sentence ≈ 17 tokens; need >32*512 ≈ 16384 tokens ≈ 964 sentences
        val sentence = "This sentence is used to generate many chunks for batch testing purposes here. "
        val text = sentence.repeat(1500)

        val result = service.ingest(
            fileBytes = text.toByteArray(),
            filename = "large.txt",
            mimeType = "text/plain",
        )

        assertTrue(result.chunkCount > 32, "Should produce >32 chunks, got ${result.chunkCount}")

        val query = db.documentChunkBox.query()
            .equal(DocumentChunk_.documentId, result.documentId)
            .build()
        val chunks = query.find()
        query.close()
        assertEquals(result.chunkCount, chunks.size)

        // Verify chunk indices are sequential
        val indices = chunks.map { it.chunkIndex }.sorted()
        assertEquals((0 until result.chunkCount).toList(), indices)
    }
}
