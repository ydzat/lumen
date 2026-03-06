package com.lumen.core.database

import com.lumen.core.database.entities.MemoryEntry
import com.lumen.core.database.entities.MyObjectBox
import com.lumen.core.database.entities.Source
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LumenDatabaseTest {

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "objectbox-test-${System.nanoTime()}")
        tempDir.mkdirs()
        val store = MyObjectBox.builder()
            .baseDirectory(tempDir)
            .build()
        db = LumenDatabase(store)
    }

    @AfterTest
    fun teardown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun putAndGetSource() {
        val source = Source(name = "Test Feed", url = "https://example.com/feed", type = "rss")
        val id = db.sourceBox.put(source)
        assertNotEquals(0, id)

        val retrieved = db.sourceBox.get(id)
        assertEquals("Test Feed", retrieved.name)
        assertEquals("https://example.com/feed", retrieved.url)
        assertEquals("rss", retrieved.type)
        assertTrue(retrieved.enabled)
    }

    @Test
    fun updateSource() {
        val source = Source(name = "Old Name", url = "https://example.com", type = "rss")
        val id = db.sourceBox.put(source)

        val updated = db.sourceBox.get(id).copy(name = "New Name", enabled = false)
        db.sourceBox.put(updated)

        val retrieved = db.sourceBox.get(id)
        assertEquals("New Name", retrieved.name)
        assertEquals(false, retrieved.enabled)
    }

    @Test
    fun deleteSource() {
        val source = Source(name = "To Delete", url = "https://example.com", type = "rss")
        val id = db.sourceBox.put(source)
        assertTrue(db.sourceBox.get(id) != null)

        db.sourceBox.remove(id)
        assertEquals(null, db.sourceBox.get(id))
    }

    @Test
    fun getAllSources() {
        db.sourceBox.put(Source(name = "Feed 1", url = "https://a.com", type = "rss"))
        db.sourceBox.put(Source(name = "Feed 2", url = "https://b.com", type = "atom"))

        val all = db.sourceBox.all
        assertEquals(2, all.size)
    }

    @Test
    fun putMemoryEntryWithEmbedding() {
        val embedding = FloatArray(1536) { it.toFloat() / 1536f }
        val entry = MemoryEntry(
            content = "Test memory",
            category = "conversation",
            source = "chat",
            embedding = embedding,
            importance = 0.8f
        )
        val id = db.memoryEntryBox.put(entry)
        assertNotEquals(0, id)

        val retrieved = db.memoryEntryBox.get(id)
        assertEquals("Test memory", retrieved.content)
        assertEquals("conversation", retrieved.category)
        assertEquals(0.8f, retrieved.importance)
        assertEquals(1536, retrieved.embedding.size)
        assertTrue(retrieved.embedding.contentEquals(embedding))
    }
}
