package com.lumen.server

import com.lumen.companion.agent.ContextWindowBuilder
import com.lumen.companion.agent.LumenAgent
import com.lumen.companion.conversation.ConversationManager
import com.lumen.companion.persona.PersonaManager
import com.lumen.core.config.LlmConfig
import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Conversation
import com.lumen.core.database.entities.EMBEDDING_DIMENSIONS
import com.lumen.core.database.entities.Message
import com.lumen.core.database.entities.MyObjectBox
import com.lumen.core.database.entities.Persona
import com.lumen.core.memory.EmbeddingClient
import com.lumen.core.memory.LlmCall
import com.lumen.server.dto.ConversationCreateRequest
import com.lumen.server.dto.ConversationDetailDto
import com.lumen.server.dto.ConversationDto
import com.lumen.server.dto.PersonaCreateRequest
import com.lumen.server.dto.PersonaDto
import com.lumen.server.dto.SendMessageRequest
import com.lumen.server.plugins.AUTH_BEARER
import com.lumen.server.plugins.configureAuth
import com.lumen.server.plugins.configureErrorHandling
import com.lumen.server.plugins.configureSerialization
import com.lumen.server.routes.chatRoutes
import com.lumen.server.routes.personaRoutes
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

class ChatRoutesTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val testToken = "test-token"

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File

    private val fakeEmbeddingClient = object : EmbeddingClient {
        override suspend fun embed(text: String) = FloatArray(EMBEDDING_DIMENSIONS)
        override suspend fun embedBatch(texts: List<String>) = texts.map { FloatArray(EMBEDDING_DIMENSIONS) }
    }

    private val fakeLlmCall = LlmCall { _, _ -> """{"response":"Hello from test"}""" }

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "lumen-chat-test-${System.nanoTime()}")
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
        single { ConversationManager(get()) }
        single { PersonaManager(get()) }
        single { ContextWindowBuilder(null) }
        factory { (projectId: Long, personaId: Long) ->
            LumenAgent(
                config = LlmConfig(provider = "deepseek", apiKey = "test-key"),
                conversationManager = get(),
                contextWindowBuilder = get(),
                projectId = projectId,
            )
        }
    }

    // --- Conversations ---

    @Test
    fun listConversations_returnsJson() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { chatRoutes() }
                }
            }
        }

        val response = client.get("/api/conversations") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<List<ConversationDto>>(response.bodyAsText())
        assertTrue(body.isEmpty())
    }

    @Test
    fun createConversation_returnsCreated() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { chatRoutes() }
                }
            }
        }

        val request = ConversationCreateRequest(title = "Test Chat", personaId = 0, projectId = 0)
        val response = client.post("/api/conversations") {
            bearerAuth(testToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = json.decodeFromString<ConversationDto>(response.bodyAsText())
        assertEquals("Test Chat", body.title)
        assertTrue(body.id > 0)
    }

    @Test
    fun getConversation_withMessages_returnsDetail() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { chatRoutes() }
                }
            }
        }

        val now = System.currentTimeMillis()
        val convId = db.conversationBox.put(Conversation(
            title = "Test Conversation",
            createdAt = now,
            updatedAt = now,
        ))
        db.messageBox.put(Message(
            conversationId = convId,
            role = "user",
            content = "Hello",
            createdAt = now,
        ))
        db.messageBox.put(Message(
            conversationId = convId,
            role = "assistant",
            content = "Hi there!",
            createdAt = now + 1,
        ))

        val response = client.get("/api/conversations/$convId") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<ConversationDetailDto>(response.bodyAsText())
        assertEquals("Test Conversation", body.conversation.title)
        assertEquals(2, body.messages.size)
        assertEquals("user", body.messages[0].role)
        assertEquals("assistant", body.messages[1].role)
    }

    @Test
    fun getConversation_notFound_returns404() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { chatRoutes() }
                }
            }
        }

        val response = client.get("/api/conversations/999") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun deleteConversation_returns200() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { chatRoutes() }
                }
            }
        }

        val now = System.currentTimeMillis()
        val convId = db.conversationBox.put(Conversation(
            title = "To Delete",
            createdAt = now,
            updatedAt = now,
        ))

        val response = client.delete("/api/conversations/$convId") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun deleteConversation_notFound_returns404() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { chatRoutes() }
                }
            }
        }

        val response = client.delete("/api/conversations/999") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun sendMessage_returnsSSE() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { chatRoutes() }
                }
            }
        }

        val now = System.currentTimeMillis()
        val convId = db.conversationBox.put(Conversation(
            title = "SSE Test",
            createdAt = now,
            updatedAt = now,
        ))

        val request = SendMessageRequest(content = "Hello")
        val response = client.post("/api/conversations/$convId/messages") {
            bearerAuth(testToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.contentType()?.match(ContentType.Text.EventStream) == true)

        val body = response.bodyAsText()
        assertTrue(body.contains("event: message_start"))
        assertTrue(body.contains("event: message_end"))
    }

    // --- Personas ---

    @Test
    fun listPersonas_returnsJson() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { personaRoutes() }
                }
            }
        }

        db.personaBox.put(Persona(
            name = "Test Persona",
            systemPrompt = "You are a test.",
            isBuiltIn = false,
            createdAt = System.currentTimeMillis(),
        ))

        val response = client.get("/api/personas") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<List<PersonaDto>>(response.bodyAsText())
        assertEquals(1, body.size)
        assertEquals("Test Persona", body[0].name)
    }

    @Test
    fun createPersona_returnsCreated() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { personaRoutes() }
                }
            }
        }

        val request = PersonaCreateRequest(
            name = "Custom Bot",
            systemPrompt = "You are a custom bot.",
            greeting = "Hello!",
            avatarEmoji = "robot",
        )
        val response = client.post("/api/personas") {
            bearerAuth(testToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = json.decodeFromString<PersonaDto>(response.bodyAsText())
        assertEquals("Custom Bot", body.name)
        assertEquals(false, body.isBuiltIn)
        assertTrue(body.id > 0)
    }

    @Test
    fun updatePersona_returnsUpdated() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { personaRoutes() }
                }
            }
        }

        val id = db.personaBox.put(Persona(
            name = "Old Name",
            systemPrompt = "Old prompt",
            isBuiltIn = false,
            createdAt = System.currentTimeMillis(),
        ))

        val request = PersonaCreateRequest(name = "New Name", systemPrompt = "New prompt")
        val response = client.put("/api/personas/$id") {
            bearerAuth(testToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<PersonaDto>(response.bodyAsText())
        assertEquals("New Name", body.name)
        assertEquals("New prompt", body.systemPrompt)
    }

    @Test
    fun deleteBuiltInPersona_returns400() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { personaRoutes() }
                }
            }
        }

        val id = db.personaBox.put(Persona(
            name = "Built-in",
            systemPrompt = "System prompt",
            isBuiltIn = true,
            createdAt = System.currentTimeMillis(),
        ))

        val response = client.delete("/api/personas/$id") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun deletePersona_returns200() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { personaRoutes() }
                }
            }
        }

        val id = db.personaBox.put(Persona(
            name = "Deletable",
            systemPrompt = "Prompt",
            isBuiltIn = false,
            createdAt = System.currentTimeMillis(),
        ))

        val response = client.delete("/api/personas/$id") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun activatePersona_returns200() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { personaRoutes() }
                }
            }
        }

        val id = db.personaBox.put(Persona(
            name = "Activate Me",
            systemPrompt = "Prompt",
            isBuiltIn = false,
            isActive = false,
            createdAt = System.currentTimeMillis(),
        ))

        val response = client.post("/api/personas/$id/activate") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun activatePersona_notFound_returns404() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { personaRoutes() }
                }
            }
        }

        val response = client.post("/api/personas/999/activate") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // --- Auth ---

    @Test
    fun conversations_withoutAuth_returns401() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            install(Koin) { modules(testKoinModule()) }
            routing {
                authenticate(AUTH_BEARER) {
                    route("/api") { chatRoutes() }
                }
            }
        }

        val response = client.get("/api/conversations")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
