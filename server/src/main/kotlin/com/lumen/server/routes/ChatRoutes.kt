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
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.koin.core.parameter.parametersOf
import org.koin.ktor.ext.get as koinGet
import org.koin.ktor.ext.getKoin

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

            call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
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
        JsonObject(mapOf("messageId" to JsonPrimitive(event.messageId))).toString()
    is ChatEvent.AssistantResponse -> "content_delta" to
        JsonObject(mapOf("text" to JsonPrimitive(event.text))).toString()
    is ChatEvent.ToolCallStart -> "tool_call" to
        JsonObject(mapOf("toolName" to JsonPrimitive(event.toolName), "args" to JsonPrimitive(event.args))).toString()
    is ChatEvent.ToolCallResult -> "tool_result" to
        JsonObject(mapOf("toolName" to JsonPrimitive(event.toolName), "result" to JsonPrimitive(event.result))).toString()
    is ChatEvent.MemoryRecalled -> "memory_recalled" to
        JsonObject(mapOf("count" to JsonPrimitive(event.count))).toString()
    is ChatEvent.MemoryExtracted -> "memory_extracted" to
        JsonObject(mapOf("count" to JsonPrimitive(event.count))).toString()
    is ChatEvent.TitleGenerated -> "title_generated" to
        JsonObject(mapOf("title" to JsonPrimitive(event.title))).toString()
    is ChatEvent.Error -> "error" to
        JsonObject(mapOf("message" to JsonPrimitive(event.message))).toString()
    is ChatEvent.Done -> "message_end" to "{}"
}
