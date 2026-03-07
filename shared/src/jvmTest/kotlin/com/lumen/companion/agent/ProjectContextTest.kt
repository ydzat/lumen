package com.lumen.companion.agent

import com.lumen.companion.agent.tools.GetProjectInfoArgs
import com.lumen.companion.agent.tools.GetProjectInfoTool
import com.lumen.companion.agent.tools.SearchArticlesArgs
import com.lumen.companion.agent.tools.SearchArticlesTool
import com.lumen.companion.agent.tools.SearchDocumentsArgs
import com.lumen.companion.agent.tools.SearchDocumentsTool
import com.lumen.core.config.LlmConfig
import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.Document
import com.lumen.core.database.entities.DocumentChunk
import com.lumen.core.database.entities.EMBEDDING_DIMENSIONS
import com.lumen.core.database.entities.MyObjectBox
import com.lumen.core.database.entities.ResearchProject
import com.lumen.core.memory.EmbeddingClient
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class ProjectContextTest {

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File

    private val fakeEmbeddingClient = object : EmbeddingClient {
        override suspend fun embed(text: String): FloatArray {
            val seed = text.hashCode()
            return FloatArray(EMBEDDING_DIMENSIONS) { i -> ((seed + i) % 100) / 100f }
        }
        override suspend fun embedBatch(texts: List<String>): List<FloatArray> =
            texts.map { embed(it) }
    }

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

    private fun createProject(name: String, description: String, keywords: String): ResearchProject {
        val project = ResearchProject(
            name = name,
            description = description,
            keywords = keywords,
            embedding = FloatArray(EMBEDDING_DIMENSIONS) { 0.1f },
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        val id = db.researchProjectBox.put(project)
        return db.researchProjectBox.get(id)
    }

    @Test
    fun systemPrompt_withProject_containsProjectContext() {
        val project = createProject("ML Research", "Deep learning methods", "transformers,attention")
        val agent = LumenAgent(
            LlmConfig(apiKey = "test"),
            db = db,
            projectId = project.id,
        )
        try {
            assertTrue(agent.systemPrompt.contains("Current Research Project: ML Research"))
            assertTrue(agent.systemPrompt.contains("Description: Deep learning methods"))
            assertTrue(agent.systemPrompt.contains("Keywords: transformers,attention"))
        } finally {
            agent.close()
        }
    }

    @Test
    fun systemPrompt_withoutProject_noProjectSection() {
        val agent = LumenAgent(LlmConfig(apiKey = "test"), db = db)
        try {
            assertFalse(agent.systemPrompt.contains("Current Research Project"))
        } finally {
            agent.close()
        }
    }

    @Test
    fun systemPrompt_withProjectIdZero_noProjectSection() {
        val agent = LumenAgent(LlmConfig(apiKey = "test"), db = db, projectId = 0)
        try {
            assertFalse(agent.systemPrompt.contains("Current Research Project"))
        } finally {
            agent.close()
        }
    }

    @Test
    fun tools_withProject_includesGetProjectInfo() {
        val project = createProject("Test Project", "desc", "keys")
        val agent = LumenAgent(
            LlmConfig(apiKey = "test"),
            db = db,
            embeddingClient = fakeEmbeddingClient,
            projectId = project.id,
        )
        try {
            assertTrue(agent.tools.any { it.name == "get_project_info" })
        } finally {
            agent.close()
        }
    }

    @Test
    fun tools_withoutProject_excludesGetProjectInfo() {
        val agent = LumenAgent(
            LlmConfig(apiKey = "test"),
            db = db,
            embeddingClient = fakeEmbeddingClient,
        )
        try {
            assertFalse(agent.tools.any { it.name == "get_project_info" })
        } finally {
            agent.close()
        }
    }

    @Test
    fun searchArticlesTool_withDefaultProject_filtersResults(): Unit = runBlocking {
        val project = createProject("Proj1", "", "")

        val embedding = fakeEmbeddingClient.embed("test query")
        db.articleBox.put(
            Article(title = "In Project", url = "url1", embedding = embedding, projectIds = "${project.id}"),
        )
        db.articleBox.put(
            Article(title = "Other Project", url = "url2", embedding = embedding, projectIds = "999"),
        )
        db.articleBox.put(
            Article(title = "No Project", url = "url3", embedding = embedding, projectIds = ""),
        )

        val tool = SearchArticlesTool(db, fakeEmbeddingClient, defaultProjectId = project.id)
        val result = tool.execute(SearchArticlesArgs(query = "test query"))

        assertTrue(result.contains("In Project"), "Should include project article, got: $result")
        assertFalse(result.contains("Other Project"), "Should exclude other project, got: $result")
        assertFalse(result.contains("No Project"), "Should exclude unscoped article, got: $result")
    }

    @Test
    fun searchArticlesTool_withoutProject_returnsAll(): Unit = runBlocking {
        val embedding = fakeEmbeddingClient.embed("test query")
        db.articleBox.put(
            Article(title = "Article A", url = "url1", embedding = embedding, projectIds = "1"),
        )
        db.articleBox.put(
            Article(title = "Article B", url = "url2", embedding = embedding, projectIds = ""),
        )

        val tool = SearchArticlesTool(db, fakeEmbeddingClient)
        val result = tool.execute(SearchArticlesArgs(query = "test query"))

        assertTrue(result.contains("Article A"))
        assertTrue(result.contains("Article B"))
    }

    @Test
    fun searchDocumentsTool_withDefaultProject_usesDefault(): Unit = runBlocking {
        val embedding = fakeEmbeddingClient.embed("test query")
        db.documentBox.put(Document(filename = "proj.txt", mimeType = "text/plain", chunkCount = 1, projectId = 1))
        db.documentBox.put(Document(filename = "other.txt", mimeType = "text/plain", chunkCount = 1, projectId = 2))

        db.documentChunkBox.put(
            DocumentChunk(documentId = 1, projectId = 1, chunkIndex = 0, content = "Project content", embedding = embedding),
        )
        db.documentChunkBox.put(
            DocumentChunk(documentId = 2, projectId = 2, chunkIndex = 0, content = "Other content", embedding = embedding),
        )

        val tool = SearchDocumentsTool(db, fakeEmbeddingClient, defaultProjectId = 1)
        val result = tool.execute(SearchDocumentsArgs(query = "test query"))

        assertTrue(result.contains("proj.txt"), "Should include project doc, got: $result")
        assertFalse(result.contains("other.txt"), "Should exclude other project doc, got: $result")
    }

    @Test
    fun getProjectInfoTool_returnsMetadata(): Unit = runBlocking {
        val project = createProject("AI Research", "Studying neural networks", "CNN,RNN")

        db.documentBox.put(Document(filename = "doc1.pdf", mimeType = "application/pdf", chunkCount = 1, projectId = project.id))
        db.documentBox.put(Document(filename = "doc2.pdf", mimeType = "application/pdf", chunkCount = 1, projectId = project.id))

        val embedding = fakeEmbeddingClient.embed("article")
        db.articleBox.put(Article(title = "Art1", url = "u1", embedding = embedding, projectIds = "${project.id}"))

        val tool = GetProjectInfoTool(db)
        val result = tool.execute(GetProjectInfoArgs(projectId = project.id))

        assertTrue(result.contains("AI Research"))
        assertTrue(result.contains("Studying neural networks"))
        assertTrue(result.contains("CNN,RNN"))
        assertTrue(result.contains("Documents: 2"))
        assertTrue(result.contains("Articles: 1"))
    }

    @Test
    fun getProjectInfoTool_nonExistentProject_returnsNotFound(): Unit = runBlocking {
        val tool = GetProjectInfoTool(db)
        val result = tool.execute(GetProjectInfoArgs(projectId = 999999))
        assertEquals("Project not found.", result)
    }

    @Test
    fun searchArticlesTool_explicitProjectOverridesDefault(): Unit = runBlocking {
        val embedding = fakeEmbeddingClient.embed("test query")
        db.articleBox.put(
            Article(title = "Default Proj", url = "url1", embedding = embedding, projectIds = "1"),
        )
        db.articleBox.put(
            Article(title = "Explicit Proj", url = "url2", embedding = embedding, projectIds = "2"),
        )

        val tool = SearchArticlesTool(db, fakeEmbeddingClient, defaultProjectId = 1)
        val result = tool.execute(SearchArticlesArgs(query = "test query", projectId = 2))

        assertFalse(result.contains("Default Proj"), "Should not include default project, got: $result")
        assertTrue(result.contains("Explicit Proj"), "Should include explicit project, got: $result")
    }
}
