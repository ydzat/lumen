package com.lumen.core.memory

fun interface LlmCall {
    suspend fun execute(systemPrompt: String, userPrompt: String): String
}
