package com.lumen.companion.agent

sealed interface ChatEvent {
    data class UserMessageSaved(val messageId: Long) : ChatEvent
    data class ToolCallStart(val toolName: String, val args: String) : ChatEvent
    data class ToolCallResult(val toolName: String, val result: String) : ChatEvent
    data class AssistantResponse(val text: String) : ChatEvent
    data class MemoryRecalled(val count: Int) : ChatEvent
    data class MemoryExtracted(val count: Int) : ChatEvent
    data class Error(val message: String) : ChatEvent
    data object Done : ChatEvent
}
