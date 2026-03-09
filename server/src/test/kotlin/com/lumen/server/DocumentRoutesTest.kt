package com.lumen.server

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.EMBEDDING_DIMENSIONS
import com.lumen.core.database.entities.MyObjectBox
import com.lumen.core.document.DocumentIngestionService
import com.lumen.core.document.DocumentManager
import com.lumen.core.document.DocumentParser
import com.lumen.core.document.TextChunker
import com.lumen.core.memory.EmbeddingClient
import com.lumen.server.dto.DocumentDto
import com.lumen.server.plugins.AUTH_BEARER
import com.lumen.server.plugins.configureAuth
import com.lumen.server.plugins.configureErrorHandling
import com.lumen.server.plugins.configureSerialization
import com.lumen.server.routes.documentRoutes
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
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

class DocumentRoutesTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val testToken = "test-token"

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File

    private val fakeEmbeddingClient = object : EmbeddingClient {
        override suspend fun embed(text: String) = FloatArray(EMBEDDING_DIMENSIONS)
        override suspend fun embedBatch(texts: List<String>) = texts.map { FloatArray(EMBEDDING_DIMENSIONS) }
    }

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "lumen-doc-test-${System.nanoTime()}")
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
        single { DocumentParser() }
        single { TextChunker() }
        single { DocumentIngestionService(get(), get(), get(), get()) }
        single { DocumentManager(get(), get()) }
    }

    @Test
    fun uploadDocument_validTextFile_returnsCreated() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { documentRoutes() }
                }
            }
        }

        val response = client.submitFormWithBinaryData(
            url = "/api/documents/upload",
            formData = formData {
                append("file", "Hello world. This is a test document with enough content to be processed.".toByteArray(), Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"test.txt\"")
                    append(HttpHeaders.ContentType, "text/plain")
                })
                append("projectId", "0")
            },
        ) {
            bearerAuth(testToken)
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = json.decodeFromString<DocumentDto>(response.bodyAsText())
        assertEquals("test.txt", body.filename)
        assertEquals("text/plain", body.mimeType)
        assertTrue(body.id > 0)
        assertTrue(body.chunkCount > 0)
    }

    @Test
    fun uploadDocument_emptyFile_returnsBadRequest() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { documentRoutes() }
                }
            }
        }

        val response = client.submitFormWithBinaryData(
            url = "/api/documents/upload",
            formData = formData {
                append("file", "".toByteArray(), Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"empty.txt\"")
                    append(HttpHeaders.ContentType, "text/plain")
                })
            },
        ) {
            bearerAuth(testToken)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun uploadDocument_invalidMimeType_returnsBadRequest() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { documentRoutes() }
                }
            }
        }

        val response = client.submitFormWithBinaryData(
            url = "/api/documents/upload",
            formData = formData {
                append("file", "<html></html>".toByteArray(), Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"page.html\"")
                    append(HttpHeaders.ContentType, "text/html")
                })
            },
        ) {
            bearerAuth(testToken)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun listDocuments_filtersByProject() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { documentRoutes() }
                }
            }
        }

        // Upload two documents with different projectIds
        client.submitFormWithBinaryData(
            url = "/api/documents/upload",
            formData = formData {
                append("file", "Content for project 1.".toByteArray(), Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"doc1.txt\"")
                    append(HttpHeaders.ContentType, "text/plain")
                })
                append("projectId", "1")
            },
        ) { bearerAuth(testToken) }

        client.submitFormWithBinaryData(
            url = "/api/documents/upload",
            formData = formData {
                append("file", "Content for project 2.".toByteArray(), Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"doc2.txt\"")
                    append(HttpHeaders.ContentType, "text/plain")
                })
                append("projectId", "2")
            },
        ) { bearerAuth(testToken) }

        val response = client.get("/api/documents?projectId=1") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val docs = json.decodeFromString<List<DocumentDto>>(response.bodyAsText())
        assertEquals(1, docs.size)
        assertEquals("doc1.txt", docs[0].filename)
    }

    @Test
    fun deleteDocument_returns200() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { documentRoutes() }
                }
            }
        }

        // Upload a document first
        val uploadResponse = client.submitFormWithBinaryData(
            url = "/api/documents/upload",
            formData = formData {
                append("file", "Content to delete.".toByteArray(), Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"delete-me.txt\"")
                    append(HttpHeaders.ContentType, "text/plain")
                })
            },
        ) { bearerAuth(testToken) }

        val doc = json.decodeFromString<DocumentDto>(uploadResponse.bodyAsText())

        val deleteResponse = client.delete("/api/documents/${doc.id}") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.OK, deleteResponse.status)

        // Verify deleted
        val listResponse = client.get("/api/documents?projectId=0") {
            bearerAuth(testToken)
        }
        val docs = json.decodeFromString<List<DocumentDto>>(listResponse.bodyAsText())
        assertTrue(docs.isEmpty())
    }

    @Test
    fun documents_withoutAuth_returns401() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { documentRoutes() }
                }
            }
        }

        val response = client.get("/api/documents")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
