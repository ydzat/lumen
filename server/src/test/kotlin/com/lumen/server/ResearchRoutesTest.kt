package com.lumen.server

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.EMBEDDING_DIMENSIONS
import com.lumen.core.database.entities.MyObjectBox
import com.lumen.core.database.entities.ResearchProject
import com.lumen.core.database.entities.Source
import com.lumen.core.memory.EmbeddingClient
import com.lumen.core.memory.LlmCall
import com.lumen.research.ProjectManager
import com.lumen.research.analyzer.ArticleAnalyzer
import com.lumen.research.analyzer.RelevanceScorer
import com.lumen.research.collector.CollectorManager
import com.lumen.research.collector.RssCollector
import com.lumen.research.collector.SourceManager
import com.lumen.research.digest.DigestGenerator
import com.lumen.server.config.ServerConfigStore
import com.lumen.server.dto.AnalyzeResponse
import com.lumen.server.dto.ArticleDto
import com.lumen.server.dto.ArticleListResponse
import com.lumen.core.database.entities.Digest
import com.lumen.server.dto.DigestDto
import com.lumen.server.dto.ProjectCreateRequest
import com.lumen.server.dto.ProjectDto
import com.lumen.server.dto.RefreshResponse
import com.lumen.server.dto.SourceCreateRequest
import com.lumen.server.dto.TrendsResponse
import com.lumen.server.notification.NtfyNotifier
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import com.lumen.server.dto.SourceDto
import com.lumen.server.plugins.AUTH_BEARER
import com.lumen.server.plugins.configureAuth
import com.lumen.server.plugins.configureErrorHandling
import com.lumen.server.plugins.configureSerialization
import com.lumen.server.routes.articleRoutes
import com.lumen.server.routes.digestRoutes
import com.lumen.server.routes.projectRoutes
import com.lumen.server.routes.sourceRoutes
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResearchRoutesTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val testToken = "test-token"

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File

    private val fakeEmbeddingClient = object : EmbeddingClient {
        override suspend fun embed(text: String) = FloatArray(EMBEDDING_DIMENSIONS)
        override suspend fun embedBatch(texts: List<String>) = texts.map { FloatArray(EMBEDDING_DIMENSIONS) }
    }

    private val fakeLlmCall = LlmCall { _, _ -> """{"title":"Test","highlights":"Test","trends":""}""" }

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "lumen-route-test-${System.nanoTime()}")
        tempDir.mkdirs()
        val store = MyObjectBox.builder().baseDirectory(tempDir).build()
        db = LumenDatabase(store)
    }

    @AfterTest
    fun teardown() {
        stopKoin()
        db.close()
        tempDir.deleteRecursively()
    }

    private fun testKoinModule() = module {
        single { db }
        single { fakeEmbeddingClient as EmbeddingClient }
        single { fakeLlmCall as LlmCall }
        single { SourceManager(get()) }
        single { RssCollector(get()) }
        single { ProjectManager(get(), get()) }
        single { ArticleAnalyzer(get(), get(), get()) }
        single { RelevanceScorer(get(), null) }
        single { DigestGenerator(get(), get(), null) }
        single { CollectorManager(get(), get(), get(), get()) }
        single { ServerConfigStore(tempDir) }
        single { NtfyNotifier(HttpClient(MockEngine { respond("ok") }), get()) }
    }

    // --- Articles ---

    @Test
    fun listArticles_returnsPagedJson() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { articleRoutes() }
                }
            }
        }

        db.articleBox.put(
            Article(title = "Article 1", url = "http://a.com/1", fetchedAt = 1000),
            Article(title = "Article 2", url = "http://a.com/2", fetchedAt = 2000),
        )

        val response = client.get("/api/articles?page=1&size=10") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<ArticleListResponse>(response.bodyAsText())
        assertEquals(2, body.total)
        assertEquals(1, body.page)
        assertEquals(10, body.size)
        assertEquals(2, body.articles.size)
    }

    @Test
    fun listArticles_withKeywordFilter_returnsMatching() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { articleRoutes() }
                }
            }
        }

        db.articleBox.put(
            Article(title = "Machine Learning paper", url = "http://a.com/1", fetchedAt = 1000),
            Article(title = "Cooking recipes", url = "http://a.com/2", fetchedAt = 2000),
        )

        val response = client.get("/api/articles?keyword=machine") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<ArticleListResponse>(response.bodyAsText())
        assertEquals(1, body.total)
        assertEquals("Machine Learning paper", body.articles[0].title)
    }

    @Test
    fun getArticle_notFound_returns404() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { articleRoutes() }
                }
            }
        }

        val response = client.get("/api/articles/999") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun getArticle_existing_returnsDto() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { articleRoutes() }
                }
            }
        }

        val id = db.articleBox.put(Article(title = "Test Article", url = "http://a.com/1", fetchedAt = 1000))

        val response = client.get("/api/articles/$id") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<ArticleDto>(response.bodyAsText())
        assertEquals("Test Article", body.title)
    }

    // --- Sources ---

    @Test
    fun listSources_returnsJson() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { sourceRoutes() }
                }
            }
        }

        db.sourceBox.put(Source(name = "Test Source", url = "http://rss.example.com", type = "rss"))

        val response = client.get("/api/sources") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<List<SourceDto>>(response.bodyAsText())
        assertEquals(1, body.size)
        assertEquals("Test Source", body[0].name)
    }

    @Test
    fun createSource_returnsCreated() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { sourceRoutes() }
                }
            }
        }

        val request = SourceCreateRequest(name = "New Source", url = "http://rss.new.com", type = "rss")
        val response = client.post("/api/sources") {
            bearerAuth(testToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = json.decodeFromString<SourceDto>(response.bodyAsText())
        assertEquals("New Source", body.name)
        assertTrue(body.id > 0)
    }

    @Test
    fun deleteSource_returns200() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { sourceRoutes() }
                }
            }
        }

        val id = db.sourceBox.put(Source(name = "To Delete", url = "http://rss.del.com", type = "rss"))

        val response = client.delete("/api/sources/$id") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun deleteSource_notFound_returns404() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { sourceRoutes() }
                }
            }
        }

        val response = client.delete("/api/sources/999") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun updateSource_returnsUpdated() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { sourceRoutes() }
                }
            }
        }

        val id = db.sourceBox.put(Source(name = "Old Name", url = "http://rss.old.com", type = "rss"))

        val request = SourceCreateRequest(name = "New Name", url = "http://rss.new.com", type = "rss")
        val response = client.put("/api/sources/$id") {
            bearerAuth(testToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<SourceDto>(response.bodyAsText())
        assertEquals("New Name", body.name)
    }

    // --- Projects ---

    @Test
    fun listProjects_returnsJson() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { projectRoutes() }
                }
            }
        }

        val response = client.get("/api/projects") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<List<ProjectDto>>(response.bodyAsText())
        assertTrue(body.isEmpty())
    }

    @Test
    fun createProject_returnsCreated() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { projectRoutes() }
                }
            }
        }

        val request = ProjectCreateRequest(
            name = "AI Research",
            description = "Artificial intelligence research",
            keywords = "ai,ml,llm",
        )
        val response = client.post("/api/projects") {
            bearerAuth(testToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = json.decodeFromString<ProjectDto>(response.bodyAsText())
        assertEquals("AI Research", body.name)
        assertTrue(body.id > 0)
    }

    @Test
    fun activateProject_returns200() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { projectRoutes() }
                }
            }
        }

        val project = ResearchProject(
            name = "Test Project",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        val id = db.researchProjectBox.put(project)

        val response = client.post("/api/projects/$id/activate") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun getProject_notFound_returns404() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { projectRoutes() }
                }
            }
        }

        val response = client.get("/api/projects/999") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // --- Digest ---

    @Test
    fun getDailyDigest_returnsJson() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { digestRoutes() }
                }
            }
        }

        val response = client.get("/api/digest/daily?date=2026-03-07") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<DigestDto>(response.bodyAsText())
        assertEquals("2026-03-07", body.date)
    }

    @Test
    fun getDailyDigest_missingDate_returns400() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { digestRoutes() }
                }
            }
        }

        val response = client.get("/api/digest/daily") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun manualAnalyze_returnsPipelineResult() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { digestRoutes() }
                }
            }
        }

        val response = client.post("/api/analyze/manual") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<AnalyzeResponse>(response.bodyAsText())
        assertEquals(0, body.fetched)
    }

    @Test
    fun getTrends_returnsRecentDigests() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { digestRoutes() }
                }
            }
        }

        db.digestBox.put(
            Digest(
                date = "2026-03-07",
                title = "Test Digest",
                content = "Some trends here",
                createdAt = System.currentTimeMillis(),
            ),
        )

        val response = client.get("/api/digest/trends?days=7") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<TrendsResponse>(response.bodyAsText())
        assertEquals(7, body.days)
        assertEquals(1, body.digests.size)
        assertEquals("Test Digest", body.digests[0].title)
    }

    @Test
    fun getTrends_invalidDays_returns400() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { digestRoutes() }
                }
            }
        }

        val response = client.get("/api/digest/trends?days=0") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun getTrends_defaultDays_returns7() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { digestRoutes() }
                }
            }
        }

        val response = client.get("/api/digest/trends") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<TrendsResponse>(response.bodyAsText())
        assertEquals(7, body.days)
    }

    // --- Auth ---

    @Test
    fun articles_withoutAuth_returns401() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { articleRoutes() }
                }
            }
        }

        val response = client.get("/api/articles")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
