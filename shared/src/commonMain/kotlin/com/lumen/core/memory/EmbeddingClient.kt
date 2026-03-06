package com.lumen.core.memory

interface EmbeddingClient {
    suspend fun embed(text: String): FloatArray
    suspend fun embedBatch(texts: List<String>): List<FloatArray>
}
