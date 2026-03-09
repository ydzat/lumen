package com.lumen.companion.agent

import ai.koog.prompt.message.Message as KoogMessage
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import com.lumen.core.database.entities.Message
import com.lumen.core.memory.LlmCall

class ContextWindowBuilder(private val llmCall: LlmCall? = null) {

    suspend fun buildContext(
        messages: List<Message>,
        systemPrompt: String,
        windowSize: Int = DEFAULT_WINDOW_SIZE,
    ): List<KoogMessage> {
        val koogMessages = mutableListOf<KoogMessage>()

        koogMessages.add(KoogMessage.System(systemPrompt, RequestMetaInfo.Empty))

        if (messages.size <= windowSize) {
            koogMessages.addAll(messages.map { it.toKoogMessage() })
        } else {
            val olderMessages = messages.subList(0, messages.size - windowSize)
            val recentMessages = messages.subList(messages.size - windowSize, messages.size)

            val summary = summarizeMessages(olderMessages)
            if (summary != null) {
                koogMessages.add(
                    KoogMessage.System(
                        "Summary of earlier conversation:\n$summary",
                        RequestMetaInfo.Empty,
                    )
                )
            }

            koogMessages.addAll(recentMessages.map { it.toKoogMessage() })
        }

        return koogMessages
    }

    private suspend fun summarizeMessages(messages: List<Message>): String? {
        if (llmCall == null) return null
        val conversationText = messages.joinToString("\n") { "${it.role}: ${it.content}" }
        return try {
            llmCall.execute(SUMMARY_SYSTEM_PROMPT, conversationText)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val DEFAULT_WINDOW_SIZE = 20
        const val TOKENS_PER_WORD = 1.3f

        fun estimateTokens(text: String): Int {
            if (text.isBlank()) return 0
            return (text.split(WHITESPACE_REGEX).size * TOKENS_PER_WORD).toInt()
        }

        private val WHITESPACE_REGEX = Regex("\\s+")

        private const val SUMMARY_SYSTEM_PROMPT =
            "Summarize the following conversation in 2-3 concise paragraphs, " +
                "preserving key facts, decisions, and context that would be useful " +
                "for continuing the conversation. Do not include greetings or filler."
    }
}

private fun Message.toKoogMessage(): KoogMessage {
    val msg: KoogMessage = when (role) {
        "user" -> KoogMessage.User(content, RequestMetaInfo.Empty)
        "assistant" -> KoogMessage.Assistant(content, ResponseMetaInfo.Empty)
        "system" -> KoogMessage.System(content, RequestMetaInfo.Empty)
        "tool_call" -> KoogMessage.Tool.Call(
            id = toolCallId.ifEmpty { id.toString() },
            tool = toolName,
            content = toolArgs,
            metaInfo = ResponseMetaInfo.Empty,
        )
        "tool_result" -> KoogMessage.Tool.Result(
            id = toolCallId.ifEmpty { id.toString() },
            tool = toolName,
            content = content,
            metaInfo = RequestMetaInfo.Empty,
        )
        else -> KoogMessage.User(content, RequestMetaInfo.Empty)
    }
    return msg
}
