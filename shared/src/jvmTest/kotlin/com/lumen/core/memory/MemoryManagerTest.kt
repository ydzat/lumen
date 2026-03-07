package com.lumen.core.memory

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.MyObjectBox
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class MemoryManagerTest {

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File
    private lateinit var manager: MemoryManager

    private val fakeEmbeddingClient = object : EmbeddingClient {
        override suspend fun embed(text: String): FloatArray {
            // Deterministic fake embedding based on text hash
            val seed = text.hashCode()
            return FloatArray(1536) { i ->
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
        val fakeCompressor = SemanticCompressor(LlmCall { _, _ -> "[]" })
        manager = MemoryManager(db, fakeEmbeddingClient, fakeCompressor)
    }

    @AfterTest
    fun teardown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun store_withValidInput_persistsMemoryEntry() = runBlocking {
        val entry = manager.store("User likes coffee", "preference", "conversation")

        assertNotEquals(0L, entry.id)
        assertEquals("User likes coffee", entry.content)
        assertEquals("preference", entry.category)
        assertEquals("conversation", entry.source)
        assertEquals(1536, entry.embedding.size)

        val retrieved = db.memoryEntryBox.get(entry.id)
        assertNotNull(retrieved)
        assertEquals("User likes coffee", retrieved.content)
    }

    @Test
    fun store_setsTimestampsCorrectly() = runBlocking {
        val before = System.currentTimeMillis()
        val entry = manager.store("Test memory", "test", "test")
        val after = System.currentTimeMillis()

        assertTrue(entry.createdAt in before..after)
        assertTrue(entry.updatedAt in before..after)
        assertEquals(entry.createdAt, entry.updatedAt)
    }

    @Test
    fun recall_withMatchingQuery_returnsEntries() = runBlocking {
        manager.store("User prefers dark mode", "preference", "conversation")
        manager.store("User is a PhD student", "fact", "conversation")
        manager.store("User likes coffee in the morning", "preference", "conversation")

        val results = manager.recall("dark mode preference", limit = 3)

        assertTrue(results.isNotEmpty())
        assertTrue(results.size <= 3)
    }

    @Test
    fun recall_updatesAccessMetadata() = runBlocking {
        val entry = manager.store("Important memory", "fact", "conversation")
        assertEquals(0, entry.accessCount)
        assertEquals(0L, entry.lastAccessedAt)

        val before = System.currentTimeMillis()
        manager.recall("Important", limit = 5)
        val after = System.currentTimeMillis()

        val updated = db.memoryEntryBox.get(entry.id)
        assertNotNull(updated)
        assertEquals(1, updated.accessCount)
        assertTrue(updated.lastAccessedAt in before..after)
    }

    @Test
    fun getById_withExistingId_returnsEntry() = runBlocking {
        val entry = manager.store("Test entry", "test", "test")

        val retrieved = manager.getById(entry.id)

        assertNotNull(retrieved)
        assertEquals("Test entry", retrieved.content)
    }

    @Test
    fun getById_withNonExistentId_returnsNull() {
        val retrieved = manager.getById(99999L)
        assertNull(retrieved)
    }

    @Test
    fun delete_removesEntry() = runBlocking {
        val entry = manager.store("To be deleted", "test", "test")
        assertNotNull(manager.getById(entry.id))

        manager.delete(entry.id)

        assertNull(manager.getById(entry.id))
    }
}
