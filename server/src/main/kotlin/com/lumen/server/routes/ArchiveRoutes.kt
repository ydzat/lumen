package com.lumen.server.routes

import com.lumen.core.archive.ArchiveManager
import com.lumen.core.archive.ArchiveManifest
import com.lumen.server.dto.ImportResponse
import com.lumen.server.plugins.PayloadTooLargeException
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.get as koinGet
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

private const val MAX_ARCHIVE_SIZE = 200L * 1024 * 1024

fun Route.archiveRoutes() {
    route("/archive") {
        post("/export") {
            val archiveManager = call.application.koinGet<ArchiveManager>()
            call.response.header(
                HttpHeaders.ContentDisposition,
                "attachment; filename=\"lumen-backup.lumen\"",
            )
            call.respondOutputStream(ContentType.Application.OctetStream) {
                archiveManager.export(this)
            }
        }

        post("/import") {
            val archiveManager = call.application.koinGet<ArchiveManager>()
            val multipart = call.receiveMultipart()
            var fileBytes: ByteArray? = null

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        fileBytes = part.provider().readRemaining().readByteArray()
                    }
                    else -> {}
                }
                part.dispose()
            }

            val bytes = fileBytes
                ?: throw IllegalArgumentException("No archive file provided")

            if (bytes.size.toLong() > MAX_ARCHIVE_SIZE) {
                throw PayloadTooLargeException("Archive exceeds 200 MB limit")
            }

            val manifest = readManifest(bytes)
            archiveManager.import(ByteArrayInputStream(bytes))

            call.respond(ImportResponse(status = "ok", imported = manifest.counts))
        }
    }
}

private val json = Json { ignoreUnknownKeys = true }

private fun readManifest(archiveBytes: ByteArray): ArchiveManifest {
    ZipInputStream(ByteArrayInputStream(archiveBytes)).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (entry.name == "manifest.json") {
                val content = zip.bufferedReader().readText()
                return json.decodeFromString<ArchiveManifest>(content)
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }
    throw IllegalArgumentException("Archive missing manifest.json")
}
