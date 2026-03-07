package com.lumen.server.routes

import com.lumen.server.dto.ProjectCreateRequest
import com.lumen.server.dto.toDto
import com.lumen.server.plugins.NotFoundException
import com.lumen.research.ProjectManager
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

fun Route.projectRoutes() {
    route("/projects") {
        get {
            val projectManager = call.application.koinGet<ProjectManager>()
            val projects = projectManager.listAll()
            call.respond(projects.map { it.toDto() })
        }

        post {
            val projectManager = call.application.koinGet<ProjectManager>()
            val request = call.receive<ProjectCreateRequest>()
            val created = projectManager.create(request.name, request.description, request.keywords)
            call.respond(HttpStatusCode.Created, created.toDto())
        }

        get("/{id}") {
            val projectManager = call.application.koinGet<ProjectManager>()
            val id = call.parameters["id"]?.toLongOrNull()
                ?: throw IllegalArgumentException("Invalid project ID")
            val project = projectManager.get(id)
                ?: throw NotFoundException("Project not found: $id")
            call.respond(project.toDto())
        }

        put("/{id}") {
            val projectManager = call.application.koinGet<ProjectManager>()
            val id = call.parameters["id"]?.toLongOrNull()
                ?: throw IllegalArgumentException("Invalid project ID")
            val existing = projectManager.get(id)
                ?: throw NotFoundException("Project not found: $id")
            val request = call.receive<ProjectCreateRequest>()
            val updated = projectManager.update(
                existing.copy(
                    name = request.name,
                    description = request.description,
                    keywords = request.keywords,
                )
            )
            call.respond(updated.toDto())
        }

        delete("/{id}") {
            val projectManager = call.application.koinGet<ProjectManager>()
            val id = call.parameters["id"]?.toLongOrNull()
                ?: throw IllegalArgumentException("Invalid project ID")
            projectManager.get(id) ?: throw NotFoundException("Project not found: $id")
            projectManager.delete(id)
            call.respond(HttpStatusCode.OK)
        }

        post("/{id}/activate") {
            val projectManager = call.application.koinGet<ProjectManager>()
            val id = call.parameters["id"]?.toLongOrNull()
                ?: throw IllegalArgumentException("Invalid project ID")
            projectManager.get(id) ?: throw NotFoundException("Project not found: $id")
            projectManager.setActive(id)
            call.respond(HttpStatusCode.OK)
        }
    }
}
