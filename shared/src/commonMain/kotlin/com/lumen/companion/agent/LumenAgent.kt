package com.lumen.companion.agent

import ai.koog.agents.core.tools.Tool
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.params.LLMParams
import com.lumen.companion.agent.tools.GetDigestTool
import com.lumen.companion.agent.tools.GetProjectInfoTool
import com.lumen.companion.agent.tools.GetTrendsTool
import com.lumen.companion.agent.tools.RecallMemoryTool
import com.lumen.companion.agent.tools.SearchArticlesTool
import com.lumen.companion.agent.tools.SearchDocumentsTool
import com.lumen.companion.agent.tools.StoreMemoryTool
import com.lumen.companion.conversation.ConversationManager
import com.lumen.core.config.LlmConfig
import com.lumen.core.config.UserPreferences
import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Persona
import com.lumen.core.database.entities.ResearchProject
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
    private val userPreferences: UserPreferences = UserPreferences(),
    private val projectId: Long = 0,
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
    private val projectContext: ResearchProject? = loadProjectContext()

    internal val tools: List<Tool<*, *>> = buildTools()
    internal val systemPrompt: String = buildSystemPrompt()

    private fun loadProjectContext(): ResearchProject? {
        if (projectId <= 0 || db == null) return null
        val project = db.researchProjectBox.get(projectId)
        return if (project == null || project.id == 0L) null else project
    }

    private fun buildTools(): List<Tool<*, *>> = buildList {
        if (memoryManager != null) {
            add(RecallMemoryTool(memoryManager))
            add(StoreMemoryTool(memoryManager))
        }
        if (db != null && embeddingClient != null) {
            add(SearchArticlesTool(db, embeddingClient, defaultProjectId = projectId))
            add(SearchDocumentsTool(db, embeddingClient, defaultProjectId = projectId))
        }
        if (db != null) {
            add(GetDigestTool(db))
            add(GetTrendsTool(db))
            if (projectContext != null) {
                add(GetProjectInfoTool(db))
            }
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
                val responses = RetryExecutor.execute(llmClient, prompt, model, tools)

                val toolCalls = responses.filterIsInstance<Message.Tool.Call>()
                if (toolCalls.isEmpty()) {
                    val responseText = responses.firstOrNull()?.content ?: ""
                    return ChatResult.Success(responseText)
                }

                // Add all tool calls first (they form one assistant message),
                // then all results — DeepSeek requires all tool_call_ids to have
                // response messages before the next turn.
                val toolResults = toolCalls.map { call -> executeToolCall(call) }
                conversationMessages.addAll(toolCalls)
                conversationMessages.addAll(toolResults)
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

            val effectiveSystemPrompt = buildSystemPromptWithRecall(userMessage)?.let { (prompt, count) ->
                emit(ChatEvent.MemoryRecalled(count))
                prompt
            } ?: systemPrompt

            val allMessages = cm.getMessages(conversationId)
            val contextMessages = cwb.buildContext(allMessages, effectiveSystemPrompt)

            val conversationMessages = contextMessages.toMutableList<Message>()
            var iterations = 0

            while (iterations < MAX_TOOL_ITERATIONS) {
                val prompt = Prompt(conversationMessages, "lumen-chat", LLMParams())
                val responses = RetryExecutor.execute(llmClient, prompt, model, tools)

                val toolCalls = responses.filterIsInstance<Message.Tool.Call>()
                if (toolCalls.isEmpty()) {
                    val responseText = responses.firstOrNull()?.content ?: ""
                    cm.addMessage(conversationId, "assistant", responseText)
                    emit(ChatEvent.AssistantResponse(responseText))
                    maybeGenerateTitle(conversationId, cm, userMessage)?.let { title ->
                        emit(ChatEvent.TitleGenerated(title))
                    }
                    maybeExtractMemories(conversationId, cm)?.let { count ->
                        emit(ChatEvent.MemoryExtracted(count))
                    }
                    break
                }

                // Execute all tool calls and collect results
                val toolResults = toolCalls.map { call ->
                    emit(ChatEvent.ToolCallStart(call.tool, call.content))
                    val result = executeToolCall(call)
                    emit(ChatEvent.ToolCallResult(call.tool, result.content))
                    val callId = call.id ?: ""
                    cm.addMessage(conversationId, "tool_call", "", call.tool, call.content, callId)
                    cm.addMessage(conversationId, "tool_result", result.content, call.tool, toolCallId = callId)
                    result
                }
                // Add all calls first, then all results — DeepSeek requires
                // all tool_call_ids to have response messages before next turn.
                conversationMessages.addAll(toolCalls)
                conversationMessages.addAll(toolResults)
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

    internal suspend fun buildSystemPromptWithRecall(
        userMessage: String,
    ): Pair<String, Int>? {
        if (!userPreferences.memoryAutoRecall) return null
        val mm = memoryManager ?: return null

        val memories = try {
            mm.recall(userMessage, limit = AUTO_RECALL_LIMIT)
        } catch (_: Exception) {
            return null
        }
        if (memories.isEmpty()) return null

        val memoryContext = memories.joinToString("\n") { "- ${it.content}" }
        val recallSection = "\n\nContext from previous conversations:\n$memoryContext"
        val personaPrompt = persona?.systemPrompt ?: DEFAULT_SYSTEM_PROMPT
        val prompt = assembleSystemPrompt(personaPrompt + recallSection)

        return prompt to memories.size
    }

    private suspend fun maybeGenerateTitle(
        conversationId: Long,
        cm: ConversationManager,
        userMessage: String,
    ): String? {
        val conversation = cm.getConversation(conversationId) ?: return null
        if (conversation.title != DEFAULT_TITLE) return null
        if (conversation.messageCount > 2) return null

        val title = generateTitle(userMessage) ?: return null
        cm.updateTitle(conversationId, title)
        return title
    }

    private suspend fun maybeExtractMemories(
        conversationId: Long,
        cm: ConversationManager,
    ): Int? {
        val interval = userPreferences.memoryExtractionInterval
        if (interval <= 0) return null
        val mm = memoryManager ?: return null
        val conversation = cm.getConversation(conversationId) ?: return null
        if (conversation.messageCount == 0 ||
            conversation.messageCount % interval != 0
        ) return null

        val messages = cm.getMessages(conversationId)
        val recentMessages = messages.takeLast(interval * 2)
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

    internal suspend fun generateTitle(userMessage: String): String? {
        if (config.apiKey.isBlank()) return null
        return try {
            val prompt = Prompt(
                listOf(
                    Message.System(TITLE_GENERATION_PROMPT, RequestMetaInfo.Empty),
                    Message.User(userMessage, RequestMetaInfo.Empty),
                ),
                "lumen-title",
                LLMParams(),
            )
            val responses = RetryExecutor.execute(llmClient, prompt, model, emptyList())
            val title = responses.firstOrNull()?.content?.trim()?.removeSurrounding("\"")
            if (title.isNullOrBlank() || title.length > MAX_TITLE_LENGTH) null else title
        } catch (_: Exception) {
            null
        }
    }

    fun close() {
        llmClient.close()
        httpClient.close()
    }

    private fun buildSystemPrompt(): String {
        val personaPrompt = persona?.systemPrompt ?: DEFAULT_SYSTEM_PROMPT
        return assembleSystemPrompt(personaPrompt)
    }

    private fun assembleSystemPrompt(basePrompt: String): String {
        val sections = buildString {
            append(basePrompt)

            if (projectContext != null) {
                append("\n\nCurrent Research Project: ${projectContext.name}")
                if (projectContext.description.isNotBlank()) {
                    append("\nDescription: ${projectContext.description}")
                }
                if (projectContext.keywords.isNotBlank()) {
                    append("\nKeywords: ${projectContext.keywords}")
                }
            }

            if (tools.isNotEmpty()) {
                val toolDescriptions = tools.joinToString("\n") { "- ${it.name}: ${it.descriptor.description}" }
                append("\n\nAvailable tools:\n")
                append(toolDescriptions)
                append("\nUse these tools when contextually appropriate.")
            }

            append("\n\nFormat your responses using Markdown when appropriate (headings, lists, bold, code blocks, etc.).")
        }
        return sections
    }

    companion object {
        internal const val DEFAULT_SYSTEM_PROMPT =
            "You are Lumen, a personal AI assistant and companion. " +
                "You help with research and daily conversations alike, remembering things across sessions. " +
                "Be warm but informative, adapting your tone to the context."
        internal const val DEFAULT_TITLE = "New conversation"
        private const val MAX_TOOL_ITERATIONS = 5
        private const val AUTO_RECALL_LIMIT = 3
        private const val CONNECT_TIMEOUT_MS = 30_000L
        private const val REQUEST_TIMEOUT_MS = 120_000L
        private const val MAX_TITLE_LENGTH = 50
        private const val TITLE_GENERATION_PROMPT =
            "Generate a concise title (max 6 words) for a conversation that starts with the following message. " +
                "Respond with only the title, no quotes, no punctuation at the end."
    }
}
