package com.lumen.core.memory

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.params.LLMParams

class KoogLlmCall(
    private val llmClient: LLMClient,
    private val model: LLModel,
) : LlmCall {

    override suspend fun execute(systemPrompt: String, userPrompt: String): String {
        val prompt = Prompt(
            listOf(
                Message.System(systemPrompt, RequestMetaInfo.Empty),
                Message.User(userPrompt, RequestMetaInfo.Empty),
            ),
            "lumen-memory",
            LLMParams(),
        )
        val responses = llmClient.execute(prompt, model, emptyList())
        return responses.firstOrNull()?.content ?: ""
    }
}
