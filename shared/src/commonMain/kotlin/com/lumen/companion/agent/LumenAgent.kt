package com.lumen.companion.agent

import ai.koog.agents.core.tools.Tool
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.params.LLMParams
import com.lumen.companion.agent.tools.RecallMemoryTool
import com.lumen.companion.agent.tools.StoreMemoryTool
import com.lumen.core.config.LlmConfig
import com.lumen.core.memory.MemoryManager
import io.ktor.client.HttpClient

class LumenAgent(
    private val config: LlmConfig,
    private val memoryManager: MemoryManager? = null,
) {

    private val httpClient = HttpClient()
    private val llmClient: LLMClient = LlmClientFactory.createClient(config, httpClient)
    private val model = LlmClientFactory.resolveModel(config)

    internal val tools: List<Tool<*, *>> = buildTools()

    private fun buildTools(): List<Tool<*, *>> {
        val manager = memoryManager ?: return emptyList()
        return listOf(RecallMemoryTool(manager), StoreMemoryTool(manager))
    }

    suspend fun chat(message: String): ChatResult {
        if (config.apiKey.isBlank()) {
            return ChatResult.Error("API key is not configured")
        }

        val messages = buildList {
            if (tools.isNotEmpty()) {
                add(Message.System(SYSTEM_PROMPT, RequestMetaInfo.Empty))
            }
            add(Message.User(message, RequestMetaInfo.Empty))
        }

        return try {
            val conversationMessages = messages.toMutableList<Message>()
            var iterations = 0

            while (iterations < MAX_TOOL_ITERATIONS) {
                val prompt = Prompt(conversationMessages, "lumen-chat", LLMParams())
                val toolDescriptors = tools.map { it.descriptor }
                val responses = llmClient.execute(prompt, model, toolDescriptors)

                val toolCall = responses.firstOrNull { it is Message.Tool.Call } as? Message.Tool.Call
                if (toolCall == null) {
                    val responseText = responses.firstOrNull()?.content ?: ""
                    return ChatResult.Success(responseText)
                }

                val toolResult = executeToolCall(toolCall)
                conversationMessages.add(toolCall)
                conversationMessages.add(toolResult)
                iterations++
            }

            ChatResult.Error("Maximum tool iterations ($MAX_TOOL_ITERATIONS) exceeded")
        } catch (e: Exception) {
            ChatResult.Error(classifyError(e), e)
        }
    }

    private suspend fun executeToolCall(call: Message.Tool.Call): Message.Tool.Result {
        val tool = tools.find { it.name == call.tool }
        val result = if (tool != null) {
            try {
                val args = tool.decodeArgs(call.contentJson)
                @Suppress("UNCHECKED_CAST")
                val typedTool = tool as Tool<Any?, Any?>
                val output = typedTool.execute(args)
                typedTool.encodeResultToStringUnsafe(output)
            } catch (e: Exception) {
                "Error executing tool '${call.tool}': ${e.message}"
            }
        } else {
            "Unknown tool: ${call.tool}"
        }

        return Message.Tool.Result(call.id, call.tool, result, RequestMetaInfo.Empty)
    }

    private fun classifyError(e: Exception): String {
        return when {
            e.message?.contains("401") == true -> "Invalid API key"
            e.message?.contains("403") == true -> "Access denied"
            e.message?.contains("429") == true -> "Rate limited, please try again later"
            e.cause?.let { it::class.simpleName } == "UnknownHostException" -> "Network unavailable"
            e.cause?.let { it::class.simpleName } == "ConnectException" -> "Cannot connect to API server"
            else -> e.message ?: "Unknown error"
        }
    }

    fun close() {
        llmClient.close()
        httpClient.close()
    }

    private companion object {
        private const val MAX_TOOL_ITERATIONS = 5

        private const val SYSTEM_PROMPT = """You are Lumen, a personal AI assistant with memory capabilities.
You have access to memory tools:
- recall_memory: Search your memories when the user asks about past conversations or stored information.
- store_memory: Save important facts, preferences, or information the user shares.
Use these tools when contextually appropriate."""
    }
}
