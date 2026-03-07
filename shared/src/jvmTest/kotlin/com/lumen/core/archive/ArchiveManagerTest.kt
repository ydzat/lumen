package com.lumen.core.archive

import com.lumen.core.config.AppConfig
import com.lumen.core.config.ConfigStore
import com.lumen.core.config.LlmConfig
import com.lumen.core.config.UserPreferences
import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.Conversation
import com.lumen.core.database.entities.Digest
import com.lumen.core.database.entities.Document
import com.lumen.core.database.entities.DocumentChunk
import com.lumen.core.database.entities.MemoryEntry
import com.lumen.core.database.entities.Message
import com.lumen.core.database.entities.MyObjectBox
import com.lumen.core.database.entities.Persona
import com.lumen.core.database.entities.ResearchProject
import com.lumen.core.database.entities.Source
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class ArchiveManagerTest {

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File
    private lateinit var configDir: File
    private lateinit var configStore: ConfigStore
    private lateinit var archiveManager: ArchiveManager

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "objectbox-archive-test-${System.nanoTime()}")
        tempDir.mkdirs()
        configDir = File(tempDir, "config")
        configDir.mkdirs()
        val store = MyObjectBox.builder()
            .baseDirectory(File(tempDir, "db"))
            .build()
        db = LumenDatabase(store)
        configStore = ConfigStore(configDir)
        archiveManager = ArchiveManager(db, configStore)
    }

    @AfterTest
    fun teardown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun export_producesValidZipWithManifest() {
        db.sourceBox.put(Source(name = "Test Source", url = "https://example.com/feed"))
        db.articleBox.put(Article(title = "Article 1", url = "https://example.com/1"))

        val output = ByteArrayOutputStream()
        archiveManager.export(output)

        val zipEntries = readZipEntryNames(output.toByteArray())
        assertTrue("manifest.json" in zipEntries)
        assertTrue("config.json" in zipEntries)
        assertTrue("sources.json" in zipEntries)
        assertTrue("articles.json" in zipEntries)
        assertTrue("digests.json" in zipEntries)
        assertTrue("projects.json" in zipEntries)
        assertTrue("conversations.json" in zipEntries)
        assertTrue("messages.json" in zipEntries)
        assertTrue("personas.json" in zipEntries)
        assertTrue("documents.json" in zipEntries)
        assertTrue("document_chunks.json" in zipEntries)
        assertTrue("memory_entries.json" in zipEntries)

        val manifestJson = readZipEntry(output.toByteArray(), "manifest.json")
        val manifest = Json.decodeFromString<ArchiveManifest>(manifestJson)
        assertEquals(1, manifest.version)
        assertEquals(1, manifest.counts["sources"])
        assertEquals(1, manifest.counts["articles"])
    }

    @Test
    fun exportImport_roundTrip_preservesData() {
        val source = Source(name = "RSS Feed", url = "https://rss.example.com", type = "rss", createdAt = 1000L)
        val sourceId = db.sourceBox.put(source)

        val project = ResearchProject(name = "ML Research", description = "Machine Learning", createdAt = 2000L)
        val projectId = db.researchProjectBox.put(project)

        val article = Article(
            sourceId = sourceId, title = "Test Article", url = "https://example.com/article",
            content = "Content here", publishedAt = 3000L, fetchedAt = 4000L,
        )
        db.articleBox.put(article)

        val digest = Digest(date = "2026-03-08", title = "Daily", content = "Summary", projectId = projectId, createdAt = 5000L)
        db.digestBox.put(digest)

        val persona = Persona(name = "Test Persona", systemPrompt = "You are helpful", createdAt = 6000L)
        val personaId = db.personaBox.put(persona)

        val conversation = Conversation(title = "Chat 1", personaId = personaId, projectId = projectId, createdAt = 7000L)
        val convId = db.conversationBox.put(conversation)

        val message = Message(conversationId = convId, role = "user", content = "Hello", createdAt = 8000L)
        db.messageBox.put(message)

        val document = Document(projectId = projectId, filename = "test.pdf", mimeType = "application/pdf", createdAt = 9000L)
        val docId = db.documentBox.put(document)

        val chunk = DocumentChunk(documentId = docId, projectId = projectId, chunkIndex = 0, content = "chunk text")
        db.documentChunkBox.put(chunk)

        val memory = MemoryEntry(content = "User likes coffee", category = "preference", source = "conversation", createdAt = 10000L)
        db.memoryEntryBox.put(memory)

        // Export
        val output = ByteArrayOutputStream()
        archiveManager.export(output)
        val archiveBytes = output.toByteArray()

        // Clear all data
        db.sourceBox.removeAll()
        db.articleBox.removeAll()
        db.digestBox.removeAll()
        db.researchProjectBox.removeAll()
        db.personaBox.removeAll()
        db.conversationBox.removeAll()
        db.messageBox.removeAll()
        db.documentBox.removeAll()
        db.documentChunkBox.removeAll()
        db.memoryEntryBox.removeAll()

        assertEquals(0, db.sourceBox.count())

        // Import
        archiveManager.import(ByteArrayInputStream(archiveBytes))

        // Verify all data restored
        assertEquals(1, db.sourceBox.count())
        assertEquals(1, db.articleBox.count())
        assertEquals(1, db.digestBox.count())
        assertEquals(1, db.researchProjectBox.count())
        assertEquals(1, db.personaBox.count())
        assertEquals(1, db.conversationBox.count())
        assertEquals(1, db.messageBox.count())
        assertEquals(1, db.documentBox.count())
        assertEquals(1, db.documentChunkBox.count())
        assertEquals(1, db.memoryEntryBox.count())

        val restoredSource = db.sourceBox.all.first()
        assertEquals("RSS Feed", restoredSource.name)
        assertEquals("https://rss.example.com", restoredSource.url)

        val restoredArticle = db.articleBox.all.first()
        assertEquals("Test Article", restoredArticle.title)
        assertEquals("https://example.com/article", restoredArticle.url)

        val restoredMessage = db.messageBox.all.first()
        assertEquals("Hello", restoredMessage.content)
        assertEquals("user", restoredMessage.role)

        val restoredMemory = db.memoryEntryBox.all.first()
        assertEquals("User likes coffee", restoredMemory.content)
    }

    @Test
    fun import_deduplicatesByUrl() {
        db.sourceBox.put(Source(name = "Existing", url = "https://example.com/feed", createdAt = 100L))
        assertEquals(1, db.sourceBox.count())

        // Create archive with same URL
        val output = ByteArrayOutputStream()
        val (otherDb, otherDir) = createTempDatabase()
        otherDb.sourceBox.put(Source(name = "Other Name", url = "https://example.com/feed", createdAt = 200L))
        val otherManager = ArchiveManager(otherDb, configStore)
        otherManager.export(output)
        otherDb.close()
        otherDir.deleteRecursively()

        archiveManager.import(ByteArrayInputStream(output.toByteArray()))

        // Should still be 1 (deduped by URL)
        assertEquals(1, db.sourceBox.count())
        assertEquals("Existing", db.sourceBox.all.first().name)
    }

    @Test
    fun import_validatesManifestVersion() {
        val badManifest = """{"version":99,"createdAt":0,"counts":{}}"""
        val archiveBytes = createMinimalZip(mapOf("manifest.json" to badManifest))

        assertFailsWith<IllegalArgumentException> {
            archiveManager.import(ByteArrayInputStream(archiveBytes))
        }
    }

    @Test
    fun export_masksApiKey() {
        configStore.save(AppConfig(
            llm = LlmConfig(apiKey = "sk-secret-key-12345"),
            preferences = UserPreferences(),
        ))

        val output = ByteArrayOutputStream()
        archiveManager.export(output)

        val configJson = readZipEntry(output.toByteArray(), "config.json")
        assertTrue("sk-secret-key-12345" !in configJson)
        assertTrue(ArchiveManager.MASKED_API_KEY in configJson)
    }

    private fun createTempDatabase(): Pair<LumenDatabase, File> {
        val dir = File(System.getProperty("java.io.tmpdir"), "objectbox-archive-other-${System.nanoTime()}")
        dir.mkdirs()
        val store = MyObjectBox.builder().baseDirectory(dir).build()
        return LumenDatabase(store) to dir
    }

    private fun readZipEntryNames(bytes: ByteArray): Set<String> {
        val names = mutableSetOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                names.add(entry.name)
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return names
    }

    private fun readZipEntry(bytes: ByteArray, name: String): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == name) {
                    return zip.bufferedReader().readText()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        throw IllegalArgumentException("Entry $name not found in ZIP")
    }

    private fun createMinimalZip(entries: Map<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
