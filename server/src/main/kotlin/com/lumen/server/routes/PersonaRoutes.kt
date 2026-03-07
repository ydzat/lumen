package com.lumen.server.routes

import com.lumen.companion.persona.PersonaManager
import com.lumen.server.dto.PersonaCreateRequest
import com.lumen.server.dto.toDto
import com.lumen.server.plugins.NotFoundException
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.koin.ktor.ext.get as koinGet

fun Route.personaRoutes() {
    route("/personas") {
        get {
            val personaManager = call.application.koinGet<PersonaManager>()
            val personas = personaManager.listAll()
            call.respond(personas.map { it.toDto() })
        }

        post {
            val personaManager = call.application.koinGet<PersonaManager>()
            val request = call.receive<PersonaCreateRequest>()
            val created = personaManager.create(
                name = request.name,
                systemPrompt = request.systemPrompt,
                greeting = request.greeting,
                avatarEmoji = request.avatarEmoji,
            )
            call.respond(HttpStatusCode.Created, created.toDto())
        }

        put("/{id}") {
            val personaManager = call.application.koinGet<PersonaManager>()
            val id = call.parameters["id"]?.toLongOrNull()
                ?: throw IllegalArgumentException("Invalid persona ID")
            val existing = personaManager.get(id)
                ?: throw NotFoundException("Persona not found: $id")
            val request = call.receive<PersonaCreateRequest>()
            val updated = personaManager.update(existing.copy(
                name = request.name,
                systemPrompt = request.systemPrompt,
                greeting = request.greeting,
                avatarEmoji = request.avatarEmoji,
            ))
            call.respond(updated.toDto())
        }

        delete("/{id}") {
            val personaManager = call.application.koinGet<PersonaManager>()
            val id = call.parameters["id"]?.toLongOrNull()
                ?: throw IllegalArgumentException("Invalid persona ID")
            personaManager.get(id) ?: throw NotFoundException("Persona not found: $id")
            personaManager.delete(id)
            call.respond(HttpStatusCode.OK)
        }

        post("/{id}/activate") {
            val personaManager = call.application.koinGet<PersonaManager>()
            val id = call.parameters["id"]?.toLongOrNull()
                ?: throw IllegalArgumentException("Invalid persona ID")
            personaManager.get(id) ?: throw NotFoundException("Persona not found: $id")
            personaManager.setActive(id)
            call.respond(HttpStatusCode.OK)
        }
    }
}
