package com.lumen.core.memory

import com.lumen.core.config.LlmConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.Closeable

class RemoteEmbeddingClient(
    private val config: LlmConfig,
    private val httpClient: HttpClient = createDefaultHttpClient(),
) : EmbeddingClient, Closeable {

    private val apiBase: String = resolveApiBase(config)

    override suspend fun embed(text: String): FloatArray {
        return embedBatch(listOf(text)).first()
    }

    override suspend fun embedBatch(texts: List<String>): List<FloatArray> {
        require(texts.isNotEmpty()) { "Input texts must not be empty" }

        val response = httpClient.post("$apiBase/v1/embeddings") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${config.apiKey}")
            setBody(EmbeddingRequest(model = config.embeddingModel, input = texts))
        }

        val body = response.body<EmbeddingResponse>()
        return body.data
            .sortedBy { it.index }
            .map { it.embedding.toFloatArray() }
    }

    override fun close() {
        httpClient.close()
    }

    companion object {
        private fun resolveApiBase(config: LlmConfig): String {
            if (config.apiBase.isNotBlank()) return config.apiBase.trimEnd('/')

            return when (config.provider) {
                "deepseek" -> "https://api.deepseek.com"
                "openai" -> "https://api.openai.com"
                "anthropic" -> throw UnsupportedOperationException(
                    "Anthropic does not provide an embedding API. " +
                        "Please configure a different provider or set a custom API base URL."
                )
                else -> throw IllegalArgumentException(
                    "API base URL is required for provider '${config.provider}'"
                )
            }
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
