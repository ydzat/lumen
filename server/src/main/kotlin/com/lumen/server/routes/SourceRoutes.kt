package com.lumen.server.routes

import com.lumen.server.dto.RefreshResponse
import com.lumen.server.dto.SourceCreateRequest
import com.lumen.server.dto.toDto
import com.lumen.server.dto.toEntity
import com.lumen.server.plugins.NotFoundException
import com.lumen.research.collector.RssCollector
import com.lumen.research.collector.SourceManager
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

fun Route.sourceRoutes() {
    route("/sources") {
        get {
            val sourceManager = call.application.koinGet<SourceManager>()
            val sources = sourceManager.listAll()
            call.respond(sources.map { it.toDto() })
        }

        post {
            val sourceManager = call.application.koinGet<SourceManager>()
            val request = call.receive<SourceCreateRequest>()
            val created = sourceManager.add(request.toEntity())
            call.respond(HttpStatusCode.Created, created.toDto())
        }

        get("/{id}") {
            val sourceManager = call.application.koinGet<SourceManager>()
            val id = call.parameters["id"]?.toLongOrNull()
                ?: throw IllegalArgumentException("Invalid source ID")
            val source = sourceManager.get(id)
                ?: throw NotFoundException("Source not found: $id")
            call.respond(source.toDto())
        }

        put("/{id}") {
            val sourceManager = call.application.koinGet<SourceManager>()
            val id = call.parameters["id"]?.toLongOrNull()
                ?: throw IllegalArgumentException("Invalid source ID")
            sourceManager.get(id) ?: throw NotFoundException("Source not found: $id")
            val request = call.receive<SourceCreateRequest>()
            val updated = sourceManager.update(request.toEntity().copy(id = id))
            call.respond(updated.toDto())
        }

        delete("/{id}") {
            val sourceManager = call.application.koinGet<SourceManager>()
            val id = call.parameters["id"]?.toLongOrNull()
                ?: throw IllegalArgumentException("Invalid source ID")
            sourceManager.get(id) ?: throw NotFoundException("Source not found: $id")
            sourceManager.remove(id)
            call.respond(HttpStatusCode.OK)
        }

        post("/{id}/refresh") {
            val sourceManager = call.application.koinGet<SourceManager>()
            val rssCollector = call.application.koinGet<RssCollector>()
            val id = call.parameters["id"]?.toLongOrNull()
                ?: throw IllegalArgumentException("Invalid source ID")
            val source = sourceManager.get(id)
                ?: throw NotFoundException("Source not found: $id")
            val articles = rssCollector.fetchSource(source)
            call.respond(RefreshResponse(fetched = articles.size))
        }
    }
}
