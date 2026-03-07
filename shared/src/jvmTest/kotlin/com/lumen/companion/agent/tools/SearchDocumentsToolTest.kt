package com.lumen.companion.agent.tools

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Document
import com.lumen.core.database.entities.DocumentChunk
import com.lumen.core.database.entities.EMBEDDING_DIMENSIONS
import com.lumen.core.database.entities.MyObjectBox
import com.lumen.core.memory.EmbeddingClient
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class SearchDocumentsToolTest {

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File
    private lateinit var tool: SearchDocumentsTool

    private val fakeEmbeddingClient = object : EmbeddingClient {
        override suspend fun embed(text: String): FloatArray {
            val seed = text.hashCode()
            return FloatArray(EMBEDDING_DIMENSIONS) { i ->
                ((seed + i) % 100) / 100f
            }
        }

        override suspend fun embedBatch(texts: List<String>): List<FloatArray> {
            return texts.map { embed(it) }
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
        tool = SearchDocumentsTool(db, fakeEmbeddingClient)
    }

    @AfterTest
    fun teardown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun execute_withNoDocuments_returnsNotFound(): Unit = runBlocking {
        val result = tool.execute(SearchDocumentsArgs(query = "anything"))
        assertTrue(result.contains("No matching documents found"))
    }

    @Test
    fun execute_withDocuments_returnsMatchingChunks(): Unit = runBlocking {
        val docId = db.documentBox.put(
            Document(filename = "test.txt", mimeType = "text/plain", chunkCount = 1),
        )
        val embedding = fakeEmbeddingClient.embed("machine learning basics")
        db.documentChunkBox.put(
            DocumentChunk(
                documentId = docId,
                chunkIndex = 0,
                content = "Machine learning is a subset of artificial intelligence.",
                embedding = embedding,
            ),
        )

        val result = tool.execute(SearchDocumentsArgs(query = "machine learning basics"))
        assertTrue(result.contains("test.txt"))
        assertTrue(result.contains("Machine learning"))
    }

    @Test
    fun execute_withProjectScoping_filtersCorrectly(): Unit = runBlocking {
        val doc1Id = db.documentBox.put(
            Document(filename = "proj1.txt", mimeType = "text/plain", chunkCount = 1, projectId = 1),
        )
        val doc2Id = db.documentBox.put(
            Document(filename = "proj2.txt", mimeType = "text/plain", chunkCount = 1, projectId = 2),
        )

        val embedding1 = fakeEmbeddingClient.embed("content for project one")
        val embedding2 = fakeEmbeddingClient.embed("content for project two")

        db.documentChunkBox.put(
            DocumentChunk(
                documentId = doc1Id, projectId = 1, chunkIndex = 0,
                content = "Content belonging to project one.",
                embedding = embedding1,
            ),
        )
        db.documentChunkBox.put(
            DocumentChunk(
                documentId = doc2Id, projectId = 2, chunkIndex = 0,
                content = "Content belonging to project two.",
                embedding = embedding2,
            ),
        )

        val result = tool.execute(
            SearchDocumentsArgs(query = "content for project one", projectId = 1),
        )
        assertTrue(result.contains("proj1.txt"), "Should include project 1 doc, got: $result")
        assertTrue(!result.contains("proj2.txt"), "Should not include project 2 doc, got: $result")
    }

    @Test
    fun execute_returnsFilenameAndChunkIndex(): Unit = runBlocking {
        val docId = db.documentBox.put(
            Document(filename = "report.pdf", mimeType = "application/pdf", chunkCount = 2),
        )
        val embedding = fakeEmbeddingClient.embed("test query")
        db.documentChunkBox.put(
            DocumentChunk(
                documentId = docId, chunkIndex = 3, content = "Chunk content here.",
                embedding = embedding,
            ),
        )

        val result = tool.execute(SearchDocumentsArgs(query = "test query"))
        assertTrue(result.contains("report.pdf"))
        assertTrue(result.contains("#3"))
    }
}
