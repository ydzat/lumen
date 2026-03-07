package com.lumen.core.database

import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.Digest
import com.lumen.core.database.entities.EMBEDDING_DIMENSIONS
import com.lumen.core.database.entities.MemoryEntry
import com.lumen.core.database.entities.MyObjectBox
import com.lumen.core.database.entities.ResearchProject
import com.lumen.core.database.entities.Article_
import com.lumen.core.database.entities.Digest_
import com.lumen.core.database.entities.Source
import io.objectbox.query.QueryBuilder
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
        assertNotNull(db.sourceBox.get(id))

        db.sourceBox.remove(id)
        assertNull(db.sourceBox.get(id))
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
        val embedding = FloatArray(EMBEDDING_DIMENSIONS) { it.toFloat() / EMBEDDING_DIMENSIONS.toFloat() }
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
        assertEquals(EMBEDDING_DIMENSIONS, retrieved.embedding.size)
        assertTrue(retrieved.embedding.contentEquals(embedding))
    }

    @Test
    fun enhancedSourceFields() {
        val source = Source(
            name = "arXiv CS.AI",
            url = "https://rss.arxiv.org/rss/cs.AI",
            type = "rss",
            category = "AI",
            description = "Artificial Intelligence papers",
            icon = "arxiv.png",
            refreshIntervalMin = 120
        )
        val id = db.sourceBox.put(source)

        val retrieved = db.sourceBox.get(id)
        assertEquals("AI", retrieved.category)
        assertEquals("Artificial Intelligence papers", retrieved.description)
        assertEquals("arxiv.png", retrieved.icon)
        assertEquals(120, retrieved.refreshIntervalMin)
    }

    @Test
    fun putAndGetArticleWithEmbedding() {
        val embedding = FloatArray(EMBEDDING_DIMENSIONS) { it.toFloat() / EMBEDDING_DIMENSIONS.toFloat() }
        val now = System.currentTimeMillis()
        val article = Article(
            sourceId = 1,
            title = "Attention Is All You Need",
            url = "https://arxiv.org/abs/1706.03762",
            summary = "We propose a new architecture...",
            author = "Vaswani et al.",
            publishedAt = now,
            fetchedAt = now,
            embedding = embedding,
            aiSummary = "Introduces the Transformer architecture",
            aiRelevanceScore = 0.95f,
            keywords = "transformer,attention,nlp",
            projectIds = "1,2"
        )
        val id = db.articleBox.put(article)
        assertNotEquals(0, id)

        val retrieved = db.articleBox.get(id)
        assertEquals("Attention Is All You Need", retrieved.title)
        assertEquals("https://arxiv.org/abs/1706.03762", retrieved.url)
        assertEquals(1L, retrieved.sourceId)
        assertEquals("Vaswani et al.", retrieved.author)
        assertFalse(retrieved.starred)
        assertEquals(0.95f, retrieved.aiRelevanceScore)
        assertEquals("transformer,attention,nlp", retrieved.keywords)
        assertEquals("1,2", retrieved.projectIds)
        assertEquals(EMBEDDING_DIMENSIONS, retrieved.embedding.size)
        assertTrue(retrieved.embedding.contentEquals(embedding))
    }

    @Test
    fun articleUrlIndexLookup() {
        val url = "https://arxiv.org/abs/1706.03762"
        db.articleBox.put(Article(title = "Paper 1", url = url))

        val query = db.articleBox.query()
            .equal(Article_.url, url, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build()
        val results = query.find()
        query.close()

        assertEquals(1, results.size)
        assertEquals("Paper 1", results[0].title)
    }

    @Test
    fun putAndGetDigest() {
        val now = System.currentTimeMillis()
        val digest = Digest(
            date = "2026-03-07",
            title = "Daily Research Digest",
            content = "Today's highlights...",
            sourceBreakdown = """{"arxiv": 5, "hackernews": 3}""",
            projectId = 1,
            createdAt = now
        )
        val id = db.digestBox.put(digest)
        assertNotEquals(0, id)

        val retrieved = db.digestBox.get(id)
        assertEquals("2026-03-07", retrieved.date)
        assertEquals("Daily Research Digest", retrieved.title)
        assertEquals(1L, retrieved.projectId)

        val query = db.digestBox.query()
            .equal(Digest_.date, "2026-03-07", QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build()
        val byDate = query.find()
        query.close()
        assertEquals(1, byDate.size)
    }

    @Test
    fun putAndGetResearchProject() {
        val embedding = FloatArray(EMBEDDING_DIMENSIONS) { it.toFloat() / EMBEDDING_DIMENSIONS.toFloat() }
        val now = System.currentTimeMillis()
        val project = ResearchProject(
            name = "NLP Thesis",
            description = "Research on transformer architectures",
            keywords = "nlp,transformer,attention",
            embedding = embedding,
            isActive = true,
            createdAt = now,
            updatedAt = now
        )
        val id = db.researchProjectBox.put(project)
        assertNotEquals(0, id)

        val retrieved = db.researchProjectBox.get(id)
        assertEquals("NLP Thesis", retrieved.name)
        assertEquals("Research on transformer architectures", retrieved.description)
        assertEquals("nlp,transformer,attention", retrieved.keywords)
        assertTrue(retrieved.isActive)
        assertEquals(EMBEDDING_DIMENSIONS, retrieved.embedding.size)
        assertTrue(retrieved.embedding.contentEquals(embedding))
    }

    @Test
    fun deleteArticle() {
        val id = db.articleBox.put(Article(title = "To Delete", url = "https://example.com"))
        assertNotNull(db.articleBox.get(id))

        db.articleBox.remove(id)
        assertNull(db.articleBox.get(id))
    }
}
