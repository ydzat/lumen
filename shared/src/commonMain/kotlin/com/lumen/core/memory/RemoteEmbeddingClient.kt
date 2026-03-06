package com.lumen.core.memory

import com.lumen.core.config.LlmConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class RemoteEmbeddingClient(
    private val config: LlmConfig,
    private val httpClient: HttpClient = createDefaultHttpClient(),
) : EmbeddingClient {

    private val apiBase: String = resolveEmbeddingApiBase(config)

    override suspend fun embed(text: String): FloatArray {
        return embedBatch(listOf(text)).first()
    }

    override suspend fun embedBatch(texts: List<String>): List<FloatArray> {
        require(texts.isNotEmpty()) { "Input texts must not be empty" }

        val response = httpClient.post("$apiBase/v1/embeddings") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
            setBody(EmbeddingRequest(model = config.embeddingModel, input = texts))
        }

        val body = response.body<EmbeddingResponse>()
        return body.data
            .sortedBy { it.index }
            .map { it.embedding.toFloatArray() }
    }

    fun close() {
        httpClient.close()
    }

    companion object {
        private fun resolveEmbeddingApiBase(config: LlmConfig): String {
            if (config.provider == "anthropic" && config.apiBase.isBlank()) {
                throw UnsupportedOperationException(
                    "Anthropic does not provide an embedding API. " +
                        "Please configure a different provider or set a custom API base URL."
                )
            }
            return config.resolveApiBase()
        }

        private fun createDefaultHttpClient(): HttpClient {
            return HttpClient {
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                        encodeDefaults = true
                    })
                }
            }
        }
    }
}

@Serializable
internal data class EmbeddingRequest(
    val model: String,
    val input: List<String>,
)

@Serializable
internal data class EmbeddingResponse(
    val data: List<EmbeddingData>,
)

@Serializable
internal data class EmbeddingData(
    val embedding: List<Float>,
    val index: Int,
)
