package com.lumen.server

import com.lumen.core.archive.ArchiveManager
import com.lumen.core.config.ConfigStore
import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.MyObjectBox
import com.lumen.core.database.entities.Source
import com.lumen.server.dto.ImportResponse
import com.lumen.server.plugins.AUTH_BEARER
import com.lumen.server.plugins.configureAuth
import com.lumen.server.plugins.configureErrorHandling
import com.lumen.server.plugins.configureSerialization
import com.lumen.server.routes.archiveRoutes
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
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
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArchiveRoutesTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val testToken = "test-token"

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File
    private lateinit var configStore: ConfigStore
    private lateinit var archiveManager: ArchiveManager

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "lumen-archive-route-test-${System.nanoTime()}")
        tempDir.mkdirs()
        val store = MyObjectBox.builder().baseDirectory(File(tempDir, "db")).build()
        db = LumenDatabase(store)
        configStore = ConfigStore(File(tempDir, "config"))
        archiveManager = ArchiveManager(db, configStore)
    }

    @AfterTest
    fun teardown() {
        stopKoin()
        db.close()
        tempDir.deleteRecursively()
    }

    private fun testKoinModule() = module {
        single { db }
        single { configStore }
        single { archiveManager }
    }

    @Test
    fun exportArchive_returnsOctetStream() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { archiveRoutes() }
                }
            }
        }

        db.sourceBox.put(Source(name = "Test", url = "https://example.com"))

        val response = client.post("/api/archive/export") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            ContentType.Application.OctetStream.toString(),
            response.headers[HttpHeaders.ContentType]?.substringBefore(";")?.trim(),
        )
        val bytes = response.readRawBytes()
        assertTrue(bytes.size > 0)
        // Verify it's a valid ZIP (ZIP magic number: PK\x03\x04)
        assertTrue(bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte())
    }

    @Test
    fun importArchive_validFile_returnsOkWithCounts() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { archiveRoutes() }
                }
            }
        }

        db.sourceBox.put(Source(name = "Feed", url = "https://example.com/feed"))

        // Export to get valid archive bytes
        val output = ByteArrayOutputStream()
        archiveManager.export(output)
        val archiveBytes = output.toByteArray()

        // Clear DB
        db.sourceBox.removeAll()
        assertEquals(0, db.sourceBox.count())

        // Import via API
        val response = client.submitFormWithBinaryData(
            url = "/api/archive/import",
            formData = formData {
                append("file", archiveBytes, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"backup.lumen\"")
                    append(HttpHeaders.ContentType, "application/octet-stream")
                })
            },
        ) {
            bearerAuth(testToken)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<ImportResponse>(response.bodyAsText())
        assertEquals("ok", body.status)
        assertEquals(1, body.imported["sources"])

        // Verify data was actually imported
        assertEquals(1, db.sourceBox.count())
    }

    @Test
    fun importArchive_invalidFile_returns400() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { archiveRoutes() }
                }
            }
        }

        val response = client.submitFormWithBinaryData(
            url = "/api/archive/import",
            formData = formData {
                append("file", "not a zip file".toByteArray(), Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"bad.lumen\"")
                    append(HttpHeaders.ContentType, "application/octet-stream")
                })
            },
        ) {
            bearerAuth(testToken)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun archive_withoutAuth_returns401() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { archiveRoutes() }
                }
            }
        }

        val response = client.post("/api/archive/export")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
