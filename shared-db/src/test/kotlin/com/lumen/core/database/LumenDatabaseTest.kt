package com.lumen.core.database

import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.Article_
import com.lumen.core.database.entities.Conversation
import com.lumen.core.database.entities.Digest
import com.lumen.core.database.entities.Digest_
import com.lumen.core.database.entities.Document
import com.lumen.core.database.entities.DocumentChunk
import com.lumen.core.database.entities.DocumentChunk_
import com.lumen.core.database.entities.EMBEDDING_DIMENSIONS
import com.lumen.core.database.entities.MemoryEntry
import com.lumen.core.database.entities.Message
import com.lumen.core.database.entities.Message_
import com.lumen.core.database.entities.MyObjectBox
import com.lumen.core.database.entities.Persona
import com.lumen.core.database.entities.ResearchProject
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

    // --- Conversation tests ---

    @Test
    fun putAndGetConversation() {
        val now = System.currentTimeMillis()
        val conversation = Conversation(
            title = "Research Discussion",
            personaId = 1,
            projectId = 2,
            messageCount = 0,
            createdAt = now,
            updatedAt = now,
        )
        val id = db.conversationBox.put(conversation)
        assertNotEquals(0, id)

        val retrieved = db.conversationBox.get(id)
        assertEquals("Research Discussion", retrieved.title)
        assertEquals(1L, retrieved.personaId)
        assertEquals(2L, retrieved.projectId)
        assertEquals(0, retrieved.messageCount)
    }

    @Test
    fun updateConversation() {
        val id = db.conversationBox.put(Conversation(title = "Old Title", createdAt = System.currentTimeMillis()))
        val updated = db.conversationBox.get(id).copy(title = "New Title", messageCount = 5)
        db.conversationBox.put(updated)

        val retrieved = db.conversationBox.get(id)
        assertEquals("New Title", retrieved.title)
        assertEquals(5, retrieved.messageCount)
    }

    @Test
    fun deleteConversation() {
        val id = db.conversationBox.put(Conversation(title = "To Delete"))
        assertNotNull(db.conversationBox.get(id))

        db.conversationBox.remove(id)
        assertNull(db.conversationBox.get(id))
    }

    // --- Message tests ---

    @Test
    fun putAndGetMessage() {
        val now = System.currentTimeMillis()
        val message = Message(
            conversationId = 1,
            role = "user",
            content = "Hello, how are you?",
            createdAt = now,
        )
        val id = db.messageBox.put(message)
        assertNotEquals(0, id)

        val retrieved = db.messageBox.get(id)
        assertEquals(1L, retrieved.conversationId)
        assertEquals("user", retrieved.role)
        assertEquals("Hello, how are you?", retrieved.content)
        assertEquals("", retrieved.toolName)
        assertEquals("", retrieved.toolArgs)
    }

    @Test
    fun putToolCallMessage() {
        val message = Message(
            conversationId = 1,
            role = "tool_call",
            content = "",
            toolName = "search_articles",
            toolArgs = """{"query": "transformer"}""",
            createdAt = System.currentTimeMillis(),
        )
        val id = db.messageBox.put(message)

        val retrieved = db.messageBox.get(id)
        assertEquals("tool_call", retrieved.role)
        assertEquals("search_articles", retrieved.toolName)
        assertEquals("""{"query": "transformer"}""", retrieved.toolArgs)
    }

    @Test
    fun queryMessagesByConversationId() {
        val convId = 42L
        db.messageBox.put(Message(conversationId = convId, role = "user", content = "Hi"))
        db.messageBox.put(Message(conversationId = convId, role = "assistant", content = "Hello!"))
        db.messageBox.put(Message(conversationId = 99, role = "user", content = "Other conv"))

        val query = db.messageBox.query()
            .equal(Message_.conversationId, convId)
            .build()
        val messages = query.find()
        query.close()

        assertEquals(2, messages.size)
        assertTrue(messages.all { it.conversationId == convId })
    }

    // --- Persona tests ---

    @Test
    fun putAndGetPersona() {
        val persona = Persona(
            name = "Research Assistant",
            systemPrompt = "You are a research assistant.",
            greeting = "How can I help with your research?",
            avatarEmoji = "book",
            isBuiltIn = true,
            isActive = true,
            createdAt = System.currentTimeMillis(),
        )
        val id = db.personaBox.put(persona)
        assertNotEquals(0, id)

        val retrieved = db.personaBox.get(id)
        assertEquals("Research Assistant", retrieved.name)
        assertEquals("You are a research assistant.", retrieved.systemPrompt)
        assertEquals("How can I help with your research?", retrieved.greeting)
        assertTrue(retrieved.isBuiltIn)
        assertTrue(retrieved.isActive)
    }

    @Test
    fun deletePersona() {
        val id = db.personaBox.put(Persona(name = "To Delete"))
        assertNotNull(db.personaBox.get(id))

        db.personaBox.remove(id)
        assertNull(db.personaBox.get(id))
    }

    // --- Document tests ---

    @Test
    fun putAndGetDocument() {
        val now = System.currentTimeMillis()
        val document = Document(
            projectId = 1,
            filename = "paper.pdf",
            mimeType = "application/pdf",
            textContent = "This is the extracted text content of the paper.",
            chunkCount = 5,
            createdAt = now,
        )
        val id = db.documentBox.put(document)
        assertNotEquals(0, id)

        val retrieved = db.documentBox.get(id)
        assertEquals(1L, retrieved.projectId)
        assertEquals("paper.pdf", retrieved.filename)
        assertEquals("application/pdf", retrieved.mimeType)
        assertEquals(5, retrieved.chunkCount)
    }

    @Test
    fun deleteDocument() {
        val id = db.documentBox.put(Document(filename = "to_delete.pdf"))
        assertNotNull(db.documentBox.get(id))

        db.documentBox.remove(id)
        assertNull(db.documentBox.get(id))
    }

    // --- DocumentChunk tests ---

    @Test
    fun putAndGetDocumentChunk() {
        val embedding = FloatArray(EMBEDDING_DIMENSIONS) { it.toFloat() / EMBEDDING_DIMENSIONS.toFloat() }
        val chunk = DocumentChunk(
            documentId = 1,
            projectId = 2,
            chunkIndex = 0,
            content = "This is the first chunk of the document.",
            embedding = embedding,
        )
        val id = db.documentChunkBox.put(chunk)
        assertNotEquals(0, id)

        val retrieved = db.documentChunkBox.get(id)
        assertEquals(1L, retrieved.documentId)
        assertEquals(2L, retrieved.projectId)
        assertEquals(0, retrieved.chunkIndex)
        assertEquals("This is the first chunk of the document.", retrieved.content)
        assertEquals(EMBEDDING_DIMENSIONS, retrieved.embedding.size)
        assertTrue(retrieved.embedding.contentEquals(embedding))
    }

    @Test
    fun queryDocumentChunksByDocumentId() {
        val embedding = FloatArray(EMBEDDING_DIMENSIONS) { 0.1f }
        db.documentChunkBox.put(DocumentChunk(documentId = 1, chunkIndex = 0, content = "Chunk 1", embedding = embedding))
        db.documentChunkBox.put(DocumentChunk(documentId = 1, chunkIndex = 1, content = "Chunk 2", embedding = embedding))
        db.documentChunkBox.put(DocumentChunk(documentId = 2, chunkIndex = 0, content = "Other doc", embedding = embedding))

        val query = db.documentChunkBox.query()
            .equal(DocumentChunk_.documentId, 1L)
            .build()
        val chunks = query.find()
        query.close()

        assertEquals(2, chunks.size)
        assertTrue(chunks.all { it.documentId == 1L })
    }

    @Test
    fun documentChunkHnswNearestNeighbor() {
        val targetEmbedding = FloatArray(EMBEDDING_DIMENSIONS) { 0.5f }
        val similarEmbedding = FloatArray(EMBEDDING_DIMENSIONS) { 0.49f }
        val dissimilarEmbedding = FloatArray(EMBEDDING_DIMENSIONS) { -0.5f }

        db.documentChunkBox.put(DocumentChunk(documentId = 1, chunkIndex = 0, content = "Similar", embedding = similarEmbedding))
        db.documentChunkBox.put(DocumentChunk(documentId = 1, chunkIndex = 1, content = "Dissimilar", embedding = dissimilarEmbedding))

        val query = db.documentChunkBox.query(
            DocumentChunk_.embedding.nearestNeighbors(targetEmbedding, 1)
        ).build()
        val results = query.find()
        query.close()

        assertEquals(1, results.size)
        assertEquals("Similar", results[0].content)
    }
}
