package com.lumen.research

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.MyObjectBox
import com.lumen.core.memory.EmbeddingClient
import kotlinx.coroutines.runBlocking
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

class ProjectManagerTest {

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File
    private lateinit var manager: ProjectManager
    private var embedCallCount = 0

    private val fakeEmbeddingClient = object : EmbeddingClient {
        override suspend fun embed(text: String): FloatArray {
            embedCallCount++
            return FloatArray(384) { text.hashCode().toFloat() / Int.MAX_VALUE }
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
        manager = ProjectManager(db, fakeEmbeddingClient)
        embedCallCount = 0
    }

    @AfterTest
    fun teardown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun create_persistsProjectWithEmbedding() = runBlocking {
        val project = manager.create("AI Safety", "Research on AI alignment", "alignment safety")

        assertNotEquals(0L, project.id)
        assertEquals("AI Safety", project.name)
        assertEquals("Research on AI alignment", project.description)
        assertEquals("alignment safety", project.keywords)
        assertTrue(project.embedding.isNotEmpty())
        assertTrue(project.createdAt > 0)
        assertTrue(project.updatedAt > 0)
        assertEquals(1, embedCallCount)
    }

    @Test
    fun get_returnsProjectById() = runBlocking {
        val project = manager.create("Test Project", "desc", "kw")

        val found = manager.get(project.id)

        assertNotNull(found)
        assertEquals("Test Project", found.name)
    }

    @Test
    fun get_returnsNullForMissingId() {
        assertNull(manager.get(999L))
    }

    @Test
    fun update_recomputesEmbeddingOnChange() = runBlocking {
        val project = manager.create("Original", "original desc", "kw1")
        assertEquals(1, embedCallCount)

        val updated = manager.update(project.copy(name = "Updated", description = "new desc"))

        assertEquals("Updated", updated.name)
        assertEquals("new desc", updated.description)
        assertEquals(2, embedCallCount)
        assertTrue(updated.updatedAt >= project.updatedAt)
    }

    @Test
    fun update_skipsEmbeddingWhenUnchanged() = runBlocking {
        val project = manager.create("Same", "same desc", "kw")
        assertEquals(1, embedCallCount)

        manager.update(project.copy(isActive = true))

        assertEquals(1, embedCallCount)
    }

    @Test
    fun delete_removesProject() = runBlocking {
        val project = manager.create("To Delete", "desc", "kw")

        manager.delete(project.id)

        assertNull(manager.get(project.id))
    }

    @Test
    fun delete_unassignsArticlesFromProject() = runBlocking {
        val project = manager.create("Project A", "desc", "kw")
        val articleId = db.articleBox.put(Article(title = "Paper 1", url = "https://example.com/1", projectIds = project.id.toString()))

        manager.delete(project.id)

        val article = db.articleBox.get(articleId)
        assertEquals("", article.projectIds)
    }

    @Test
    fun listAll_returnsAllProjects() = runBlocking {
        manager.create("Project 1", "desc1", "kw1")
        manager.create("Project 2", "desc2", "kw2")

        assertEquals(2, manager.listAll().size)
    }

    @Test
    fun setActive_deactivatesOthers() = runBlocking {
        val p1 = manager.create("P1", "d1", "k1")
        val p2 = manager.create("P2", "d2", "k2")

        manager.setActive(p1.id)
        assertTrue(manager.get(p1.id)!!.isActive)
        assertFalse(manager.get(p2.id)!!.isActive)

        manager.setActive(p2.id)
        assertFalse(manager.get(p1.id)!!.isActive)
        assertTrue(manager.get(p2.id)!!.isActive)
    }

    @Test
    fun getActive_returnsActiveProject() = runBlocking {
        val p1 = manager.create("P1", "d1", "k1")
        manager.create("P2", "d2", "k2")

        assertNull(manager.getActive())

        manager.setActive(p1.id)
        val active = manager.getActive()
        assertNotNull(active)
        assertEquals(p1.id, active.id)
    }

    @Test
    fun assignArticle_addsProjectId() = runBlocking {
        val project = manager.create("Project", "desc", "kw")
        val articleId = db.articleBox.put(Article(title = "Paper", url = "https://example.com/1"))

        manager.assignArticle(articleId, project.id)

        val article = db.articleBox.get(articleId)
        assertTrue(project.id.toString() in article.projectIds)
    }

    @Test
    fun assignArticle_doesNotDuplicate() = runBlocking {
        val project = manager.create("Project", "desc", "kw")
        val articleId = db.articleBox.put(Article(title = "Paper", url = "https://example.com/1"))

        manager.assignArticle(articleId, project.id)
        manager.assignArticle(articleId, project.id)

        val article = db.articleBox.get(articleId)
        assertEquals(project.id.toString(), article.projectIds)
    }

    @Test
    fun unassignArticle_removesProjectId() = runBlocking {
        val project = manager.create("Project", "desc", "kw")
        val articleId = db.articleBox.put(Article(title = "Paper", url = "https://example.com/1", projectIds = project.id.toString()))

        manager.unassignArticle(articleId, project.id)

        val article = db.articleBox.get(articleId)
        assertEquals("", article.projectIds)
    }

    @Test
    fun getArticlesForProject_returnsMatchingArticles() = runBlocking {
        val project = manager.create("Project", "desc", "kw")
        db.articleBox.put(Article(title = "Assigned", url = "https://example.com/1", projectIds = project.id.toString()))
        db.articleBox.put(Article(title = "Not Assigned", url = "https://example.com/2"))

        val articles = manager.getArticlesForProject(project.id)

        assertEquals(1, articles.size)
        assertEquals("Assigned", articles[0].title)
    }
}
