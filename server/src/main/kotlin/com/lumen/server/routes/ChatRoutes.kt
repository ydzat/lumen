package com.lumen.server.routes

import com.lumen.companion.agent.ChatEvent
import com.lumen.companion.agent.LumenAgent
import com.lumen.companion.conversation.ConversationManager
import com.lumen.server.dto.ConversationCreateRequest
import com.lumen.server.dto.ConversationDetailDto
import com.lumen.server.dto.SendMessageRequest
import com.lumen.server.dto.toDto
import com.lumen.server.plugins.NotFoundException
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.parameter.parametersOf
import org.koin.ktor.ext.get as koinGet
import org.koin.ktor.ext.getKoin

private val sseJson = Json { encodeDefaults = true }

fun Route.chatRoutes() {
    route("/conversations") {
        get {
            val conversationManager = call.application.koinGet<ConversationManager>()
            val conversations = conversationManager.listConversations()
            call.respond(conversations.map { it.toDto() })
        }

        post {
            val conversationManager = call.application.koinGet<ConversationManager>()
            val request = call.receive<ConversationCreateRequest>()
            val created = conversationManager.createConversation(
                title = request.title,
                personaId = request.personaId,
                projectId = request.projectId,
            )
            call.respond(HttpStatusCode.Created, created.toDto())
        }

        get("/{id}") {
            val conversationManager = call.application.koinGet<ConversationManager>()
            val id = call.parameters["id"]?.toLongOrNull()
                ?: throw IllegalArgumentException("Invalid conversation ID")
            val conversation = conversationManager.getConversation(id)
                ?: throw NotFoundException("Conversation not found: $id")
            val messages = conversationManager.getMessages(id)
            call.respond(ConversationDetailDto(
                conversation = conversation.toDto(),
                messages = messages.map { it.toDto() },
            ))
        }

        delete("/{id}") {
            val conversationManager = call.application.koinGet<ConversationManager>()
            val id = call.parameters["id"]?.toLongOrNull()
                ?: throw IllegalArgumentException("Invalid conversation ID")
            conversationManager.getConversation(id)
                ?: throw NotFoundException("Conversation not found: $id")
            conversationManager.deleteConversation(id)
            call.respond(HttpStatusCode.OK)
        }

        post("/{id}/messages") {
            val conversationManager = call.application.koinGet<ConversationManager>()
            val id = call.parameters["id"]?.toLongOrNull()
                ?: throw IllegalArgumentException("Invalid conversation ID")
            val conversation = conversationManager.getConversation(id)
                ?: throw NotFoundException("Conversation not found: $id")
            val request = call.receive<SendMessageRequest>()

            val agent = call.application.getKoin().get<LumenAgent> {
                parametersOf(conversation.projectId, conversation.personaId)
            }

            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                agent.chatStream(id, request.content).collect { event ->
                    val (eventType, data) = mapChatEvent(event)
                    write("event: $eventType\n")
                    write("data: $data\n\n")
                    flush()
                }
            }
        }
    }
}

private fun mapChatEvent(event: ChatEvent): Pair<String, String> = when (event) {
    is ChatEvent.UserMessageSaved -> "message_start" to
        sseJson.encodeToString(mapOf("messageId" to event.messageId.toString()))
    is ChatEvent.AssistantResponse -> "content_delta" to
        sseJson.encodeToString(mapOf("text" to event.text))
    is ChatEvent.ToolCallStart -> "tool_call" to
        sseJson.encodeToString(mapOf("toolName" to event.toolName, "args" to event.args))
    is ChatEvent.ToolCallResult -> "tool_result" to
        sseJson.encodeToString(mapOf("toolName" to event.toolName, "result" to event.result))
    is ChatEvent.MemoryRecalled -> "memory_recalled" to
        sseJson.encodeToString(mapOf("count" to event.count.toString()))
    is ChatEvent.MemoryExtracted -> "memory_extracted" to
        sseJson.encodeToString(mapOf("count" to event.count.toString()))
    is ChatEvent.TitleGenerated -> "title_generated" to
        sseJson.encodeToString(mapOf("title" to event.title))
    is ChatEvent.Error -> "error" to
        sseJson.encodeToString(mapOf("message" to event.message))
    is ChatEvent.Done -> "message_end" to "{}"
}
