package com.lumen.core.memory

import com.lumen.core.config.LlmConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RemoteEmbeddingClientTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun mockHttpClient(responseJson: String, status: HttpStatusCode = HttpStatusCode.OK): HttpClient {
        return HttpClient(MockEngine { respond(responseJson, status, jsonHeaders) }) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    private fun validResponse(vararg embeddings: String): String {
        val data = embeddings.mapIndexed { i, emb ->
            """{"object": "embedding", "index": $i, "embedding": [$emb]}"""
        }.joinToString(",")
        return """{"object":"list","data":[$data],"model":"text-embedding-3-small","usage":{"prompt_tokens":1,"total_tokens":1}}"""
    }

    @Test
    fun embed_withValidResponse_returnsFloatArray() = runBlocking {
        val httpClient = mockHttpClient(validResponse("0.1, 0.2, 0.3"))
        val client = RemoteEmbeddingClient(LlmConfig(provider = "openai", apiKey = "test-key"), httpClient)

        val result = client.embed("hello world")

        assertEquals(3, result.size)
        assertEquals(0.1f, result[0], 0.001f)
        assertEquals(0.2f, result[1], 0.001f)
        assertEquals(0.3f, result[2], 0.001f)
        client.close()
    }

    @Test
    fun embedBatch_withMultipleInputs_returnsMatchingResults() = runBlocking {
        val httpClient = mockHttpClient(validResponse("1.0, 2.0", "3.0, 4.0"))
        val client = RemoteEmbeddingClient(LlmConfig(provider = "openai", apiKey = "test-key"), httpClient)

        val results = client.embedBatch(listOf("hello", "world"))

        assertEquals(2, results.size)
        assertEquals(1.0f, results[0][0], 0.001f)
        assertEquals(3.0f, results[1][0], 0.001f)
        client.close()
    }

    @Test
    fun embedBatch_withUnorderedResponse_sortsByIndex() = runBlocking {
        val responseJson = """{"object":"list","data":[
            {"object":"embedding","index":1,"embedding":[3.0,4.0]},
            {"object":"embedding","index":0,"embedding":[1.0,2.0]}
        ],"model":"m","usage":{"prompt_tokens":1,"total_tokens":1}}"""
        val httpClient = mockHttpClient(responseJson)
        val client = RemoteEmbeddingClient(LlmConfig(provider = "openai", apiKey = "test-key"), httpClient)

        val results = client.embedBatch(listOf("hello", "world"))

        assertEquals(1.0f, results[0][0], 0.001f)
        assertEquals(3.0f, results[1][0], 0.001f)
        client.close()
    }

    @Test
    fun embed_withAuthError_throwsException() = runBlocking {
        val httpClient = mockHttpClient(
            """{"error":{"message":"Invalid API key"}}""",
            HttpStatusCode.Unauthorized,
        )
        val client = RemoteEmbeddingClient(LlmConfig(provider = "openai", apiKey = "bad-key"), httpClient)

        assertFailsWith<Exception> {
            client.embed("hello")
        }
        client.close()
    }

    @Test
    fun embed_withAnthropicProvider_throwsUnsupportedError() {
        val exception = assertFailsWith<UnsupportedOperationException> {
            RemoteEmbeddingClient(LlmConfig(provider = "anthropic", apiKey = "test-key"))
        }
        assertTrue(exception.message!!.contains("Anthropic"))
    }

    @Test
    fun embedBatch_withEmptyInput_throwsException() = runBlocking {
        val httpClient = mockHttpClient(validResponse("0.5"))
        val client = RemoteEmbeddingClient(LlmConfig(provider = "openai", apiKey = "test-key"), httpClient)

        assertFailsWith<IllegalArgumentException> {
            client.embedBatch(emptyList())
        }
        client.close()
    }

    @Test
    fun embed_withCustomApiBase_usesProvidedUrl() = runBlocking {
        var requestedUrl = ""
        val engine = MockEngine { request ->
            requestedUrl = request.url.toString()
            respond(validResponse("0.5"), HttpStatusCode.OK, jsonHeaders)
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val config = LlmConfig(provider = "custom", apiKey = "test-key", apiBase = "https://my-proxy.example.com")
        val client = RemoteEmbeddingClient(config, httpClient)

        client.embed("test")
        assertTrue(requestedUrl.startsWith("https://my-proxy.example.com/v1/embeddings"))
        client.close()
    }

    @Test
    fun embed_sendsCorrectAuthHeader() = runBlocking {
        var authHeader = ""
        val engine = MockEngine { request ->
            authHeader = request.headers[HttpHeaders.Authorization] ?: ""
            respond(validResponse("0.5"), HttpStatusCode.OK, jsonHeaders)
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val client = RemoteEmbeddingClient(LlmConfig(provider = "openai", apiKey = "sk-test-123"), httpClient)

        client.embed("test")
        assertEquals("Bearer sk-test-123", authHeader)
        client.close()
    }
}
