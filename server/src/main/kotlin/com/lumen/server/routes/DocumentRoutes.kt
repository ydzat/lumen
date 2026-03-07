package com.lumen.server.routes

import com.lumen.core.document.DocumentManager
import com.lumen.core.document.MimeTypes
import com.lumen.server.dto.toDto
import com.lumen.server.plugins.PayloadTooLargeException
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import org.koin.ktor.ext.get as koinGet

private const val MAX_FILE_SIZE = 50L * 1024 * 1024
private val ALLOWED_MIME_TYPES = setOf(MimeTypes.PDF, MimeTypes.PLAIN, MimeTypes.MARKDOWN)

fun Route.documentRoutes() {
    route("/documents") {
        post("/upload") {
            val documentManager = call.application.koinGet<DocumentManager>()
            val multipart = call.receiveMultipart()
            var fileBytes: ByteArray? = null
            var filename: String? = null
            var mimeType: String? = null
            var projectId: Long = 0

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        filename = part.originalFileName ?: "unknown"
                        mimeType = part.contentType?.toString() ?: MimeTypes.PLAIN
                        if (mimeType !in ALLOWED_MIME_TYPES) {
                            part.dispose()
                            throw IllegalArgumentException("Unsupported file type: $mimeType")
                        }
                        fileBytes = part.provider().readRemaining().readByteArray()
                    }
                    is PartData.FormItem -> {
                        if (part.name == "projectId") {
                            projectId = part.value.toLongOrNull() ?: 0
                        }
                    }
                    else -> {}
                }
                part.dispose()
            }

            val bytes = fileBytes
                ?: throw IllegalArgumentException("No file provided")
            val name = filename ?: "unknown"
            val type = mimeType ?: MimeTypes.PLAIN

            if (bytes.size.toLong() > MAX_FILE_SIZE) {
                throw PayloadTooLargeException("File exceeds 50 MB limit")
            }

            val document = documentManager.ingest(bytes, name, type, projectId)
            call.respond(HttpStatusCode.Created, document.toDto())
        }

        get {
            val documentManager = call.application.koinGet<DocumentManager>()
            val projectId = call.parameters["projectId"]?.toLongOrNull() ?: 0
            val documents = documentManager.listByProject(projectId)
            call.respond(documents.map { it.toDto() })
        }

        delete("/{id}") {
            val documentManager = call.application.koinGet<DocumentManager>()
            val id = call.parameters["id"]?.toLongOrNull()
                ?: throw IllegalArgumentException("Invalid document ID")
            documentManager.delete(id)
            call.respond(HttpStatusCode.OK)
        }
    }
}
