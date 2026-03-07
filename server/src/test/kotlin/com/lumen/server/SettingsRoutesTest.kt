package com.lumen.server

import com.lumen.core.config.AppConfig
import com.lumen.core.config.ConfigStore
import com.lumen.core.config.LlmConfig
import com.lumen.core.config.UserPreferences
import com.lumen.server.dto.LlmConfigDto
import com.lumen.server.dto.SettingsDto
import com.lumen.server.dto.SettingsUpdateRequest
import com.lumen.server.dto.isMaskedKey
import com.lumen.server.dto.maskApiKey
import com.lumen.server.plugins.AUTH_BEARER
import com.lumen.server.plugins.configureAuth
import com.lumen.server.plugins.configureErrorHandling
import com.lumen.server.plugins.configureSerialization
import com.lumen.server.routes.settingsRoutes
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsRoutesTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val testToken = "test-token"

    private lateinit var tempDir: File
    private lateinit var configStore: ConfigStore

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "lumen-settings-test-${System.nanoTime()}")
        tempDir.mkdirs()
        configStore = ConfigStore(tempDir)
    }

    @AfterTest
    fun teardown() {
        stopKoin()
        tempDir.deleteRecursively()
    }

    private fun testKoinModule() = module {
        single { configStore }
    }

    @Test
    fun getSettings_returnsMaskedApiKey() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { settingsRoutes() }
                }
            }
        }

        configStore.save(AppConfig(
            llm = LlmConfig(provider = "deepseek", model = "deepseek-chat", apiKey = "sk-abc123xyz"),
            preferences = UserPreferences(language = "en"),
        ))

        val response = client.get("/api/settings") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<SettingsDto>(response.bodyAsText())
        assertEquals("deepseek", body.llm.provider)
        assertEquals("deepseek-chat", body.llm.model)
        assertEquals("sk-***", body.llm.apiKey)
        assertEquals("en", body.preferences.language)
    }

    @Test
    fun getSettings_emptyApiKey_returnsEmpty() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { settingsRoutes() }
                }
            }
        }

        val response = client.get("/api/settings") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<SettingsDto>(response.bodyAsText())
        assertEquals("", body.llm.apiKey)
    }

    @Test
    fun putSettings_withMaskedKey_preservesExisting() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { settingsRoutes() }
                }
            }
        }

        configStore.save(AppConfig(
            llm = LlmConfig(provider = "deepseek", apiKey = "sk-real-secret-key"),
        ))

        val request = SettingsUpdateRequest(
            llm = LlmConfigDto(
                provider = "openai",
                model = "gpt-4",
                apiKey = "sk-***",
                apiBase = "",
            ),
            preferences = UserPreferences(language = "en"),
        )
        val response = client.put("/api/settings") {
            bearerAuth(testToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val saved = configStore.load()
        assertEquals("openai", saved.llm.provider)
        assertEquals("gpt-4", saved.llm.model)
        assertEquals("sk-real-secret-key", saved.llm.apiKey)
        assertEquals("en", saved.preferences.language)
    }

    @Test
    fun putSettings_withNewKey_updatesKey() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { settingsRoutes() }
                }
            }
        }

        configStore.save(AppConfig(
            llm = LlmConfig(apiKey = "old-key"),
        ))

        val request = SettingsUpdateRequest(
            llm = LlmConfigDto(
                provider = "deepseek",
                model = "deepseek-chat",
                apiKey = "sk-brand-new-key-12345",
                apiBase = "",
            ),
            preferences = UserPreferences(),
        )
        val response = client.put("/api/settings") {
            bearerAuth(testToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val saved = configStore.load()
        assertEquals("sk-brand-new-key-12345", saved.llm.apiKey)
    }

    @Test
    fun putSettings_responseHasMaskedKey() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { settingsRoutes() }
                }
            }
        }

        val request = SettingsUpdateRequest(
            llm = LlmConfigDto(
                provider = "deepseek",
                model = "deepseek-chat",
                apiKey = "sk-new-secret",
                apiBase = "",
            ),
            preferences = UserPreferences(),
        )
        val response = client.put("/api/settings") {
            bearerAuth(testToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<SettingsDto>(response.bodyAsText())
        assertEquals("sk-***", body.llm.apiKey)
    }

    @Test
    fun settings_withoutAuth_returns401() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { settingsRoutes() }
                }
            }
        }

        val response = client.get("/api/settings")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // --- Unit tests for masking logic ---

    @Test
    fun maskApiKey_blankKey_returnsEmpty() {
        assertEquals("", maskApiKey(""))
        assertEquals("", maskApiKey("   "))
    }

    @Test
    fun maskApiKey_shortKey_returnsFullMask() {
        assertEquals("***", maskApiKey("ab"))
        assertEquals("***", maskApiKey("abc"))
    }

    @Test
    fun maskApiKey_normalKey_returnsPartialMask() {
        assertEquals("sk-***", maskApiKey("sk-abc123"))
    }

    @Test
    fun isMaskedKey_detectsMaskedValues() {
        assertTrue(isMaskedKey(""))
        assertTrue(isMaskedKey("***"))
        assertTrue(isMaskedKey("sk-***"))
        assertFalse(isMaskedKey("sk-real-key-123"))
    }
}
