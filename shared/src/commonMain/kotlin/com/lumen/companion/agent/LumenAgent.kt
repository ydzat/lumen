package com.lumen.companion.agent

import ai.koog.agents.core.tools.Tool
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.params.LLMParams
import com.lumen.companion.agent.tools.GetDigestTool
import com.lumen.companion.agent.tools.GetTrendsTool
import com.lumen.companion.agent.tools.RecallMemoryTool
import com.lumen.companion.agent.tools.SearchArticlesTool
import com.lumen.companion.agent.tools.StoreMemoryTool
import com.lumen.companion.conversation.ConversationManager
import com.lumen.core.config.LlmConfig
import com.lumen.core.database.entities.Persona
import com.lumen.core.database.LumenDatabase
import com.lumen.core.memory.EmbeddingClient
import com.lumen.core.memory.MemoryManager
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class LumenAgent(
    private val config: LlmConfig,
    private val memoryManager: MemoryManager? = null,
    private val db: LumenDatabase? = null,
    private val embeddingClient: EmbeddingClient? = null,
    private val conversationManager: ConversationManager? = null,
    private val contextWindowBuilder: ContextWindowBuilder? = null,
    private val persona: Persona? = null,
) {

    private val httpClient = HttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            socketTimeoutMillis = REQUEST_TIMEOUT_MS
        }
    }
    private val llmClient: LLMClient = LlmClientFactory.createClient(config, httpClient)
    private val model = LlmClientFactory.resolveModel(config)

    internal val tools: List<Tool<*, *>> = buildTools()
    internal val systemPrompt: String = buildSystemPrompt()

    private fun buildTools(): List<Tool<*, *>> = buildList {
        if (memoryManager != null) {
            add(RecallMemoryTool(memoryManager))
            add(StoreMemoryTool(memoryManager))
        }
        if (db != null && embeddingClient != null) {
            add(SearchArticlesTool(db, embeddingClient))
        }
        if (db != null) {
            add(GetDigestTool(db))
            add(GetTrendsTool(db))
        }
    }

    suspend fun chat(message: String): ChatResult {
        if (config.apiKey.isBlank()) {
            return ChatResult.Error("API key is not configured")
        }

        val messages = buildList {
            add(Message.System(systemPrompt, RequestMetaInfo.Empty))
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

    fun chatStream(conversationId: Long, userMessage: String): Flow<ChatEvent> = flow {
        val cm = conversationManager
            ?: throw IllegalStateException("ConversationManager is required for chatStream")
        val cwb = contextWindowBuilder ?: ContextWindowBuilder()

        if (config.apiKey.isBlank()) {
            emit(ChatEvent.Error("API key is not configured"))
            emit(ChatEvent.Done)
            return@flow
        }

        try {
            val userMsg = cm.addMessage(conversationId, "user", userMessage)
            emit(ChatEvent.UserMessageSaved(userMsg.id))

            val allMessages = cm.getMessages(conversationId)
            val contextMessages = cwb.buildContext(allMessages, systemPrompt)

            val conversationMessages = contextMessages.toMutableList<Message>()
            var iterations = 0

            while (iterations < MAX_TOOL_ITERATIONS) {
                val prompt = Prompt(conversationMessages, "lumen-chat", LLMParams())
                val toolDescriptors = tools.map { it.descriptor }
                val responses = llmClient.execute(prompt, model, toolDescriptors)

                val toolCall = responses.firstOrNull { it is Message.Tool.Call } as? Message.Tool.Call
                if (toolCall == null) {
                    val responseText = responses.firstOrNull()?.content ?: ""
                    cm.addMessage(conversationId, "assistant", responseText)
                    emit(ChatEvent.AssistantResponse(responseText))
                    maybeExtractMemories(conversationId, cm)?.let { count ->
                        emit(ChatEvent.MemoryExtracted(count))
                    }
                    break
                }

                emit(ChatEvent.ToolCallStart(toolCall.tool, toolCall.content))
                val toolResult = executeToolCall(toolCall)
                emit(ChatEvent.ToolCallResult(toolCall.tool, toolResult.content))

                cm.addMessage(conversationId, "tool_call", "", toolCall.tool, toolCall.content)
                cm.addMessage(conversationId, "tool_result", toolResult.content, toolCall.tool)

                conversationMessages.add(toolCall)
                conversationMessages.add(toolResult)
                iterations++
            }

            if (iterations >= MAX_TOOL_ITERATIONS) {
                val errorMsg = "Maximum tool iterations ($MAX_TOOL_ITERATIONS) exceeded"
                cm.addMessage(conversationId, "assistant", errorMsg)
                emit(ChatEvent.Error(errorMsg))
            }
        } catch (e: Exception) {
            emit(ChatEvent.Error(classifyError(e)))
        }

        emit(ChatEvent.Done)
    }

    private suspend fun maybeExtractMemories(
        conversationId: Long,
        cm: ConversationManager,
    ): Int? {
        val mm = memoryManager ?: return null
        val conversation = cm.getConversation(conversationId) ?: return null
        if (conversation.messageCount == 0 ||
            conversation.messageCount % MEMORY_EXTRACTION_INTERVAL != 0
        ) return null

        val messages = cm.getMessages(conversationId)
        val recentMessages = messages.takeLast(MEMORY_EXTRACTION_INTERVAL * 2)
        val conversationText = recentMessages
            .filter { it.role == "user" || it.role == "assistant" }
            .joinToString("\n") { "${it.role}: ${it.content}" }
        if (conversationText.isBlank()) return null

        return try {
            val extracted = mm.storeFromConversation(conversationText)
            extracted.size.takeIf { it > 0 }
        } catch (_: Exception) {
            null
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

    private fun buildSystemPrompt(): String {
        val personaPrompt = persona?.systemPrompt
            ?: DEFAULT_SYSTEM_PROMPT

        if (tools.isEmpty()) return personaPrompt

        val toolDescriptions = tools.joinToString("\n") { "- ${it.name}: ${it.descriptor.description}" }
        return """$personaPrompt

Available tools:
$toolDescriptions
Use these tools when contextually appropriate."""
    }

    companion object {
        internal const val DEFAULT_SYSTEM_PROMPT = "You are Lumen, a personal AI assistant."
        private const val MAX_TOOL_ITERATIONS = 5
        private const val MEMORY_EXTRACTION_INTERVAL = 10
        private const val CONNECT_TIMEOUT_MS = 30_000L
        private const val REQUEST_TIMEOUT_MS = 120_000L
    }
}
