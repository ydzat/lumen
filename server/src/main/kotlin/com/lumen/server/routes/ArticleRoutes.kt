package com.lumen.server.routes

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article_
import com.lumen.server.dto.ArticleListResponse
import com.lumen.server.dto.toDto
import com.lumen.server.plugins.NotFoundException
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.koin.ktor.ext.get as koinGet

fun Route.articleRoutes() {
    route("/articles") {
        get {
            val db = call.application.koinGet<LumenDatabase>()
            val sourceId = call.request.queryParameters["sourceId"]?.toLongOrNull()
            val keyword = call.request.queryParameters["keyword"]
            val dateFrom = call.request.queryParameters["dateFrom"]?.toLongOrNull()
            val dateTo = call.request.queryParameters["dateTo"]?.toLongOrNull()
            val page = (call.request.queryParameters["page"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
            val size = (call.request.queryParameters["size"]?.toIntOrNull() ?: 20).coerceIn(1, 100)

            val queryBuilder = db.articleBox.query()

            if (sourceId != null) {
                queryBuilder.equal(Article_.sourceId, sourceId)
            }
            if (dateFrom != null) {
                queryBuilder.greaterOrEqual(Article_.fetchedAt, dateFrom)
            }
            if (dateTo != null) {
                queryBuilder.lessOrEqual(Article_.fetchedAt, dateTo)
            }

            queryBuilder.orderDesc(Article_.fetchedAt)

            val query = queryBuilder.build()
            val allResults = query.use { q ->
                val results = q.find()
                if (keyword.isNullOrBlank()) {
                    results
                } else {
                    val lower = keyword.lowercase()
                    results.filter { article ->
                        article.title.lowercase().contains(lower) ||
                            article.summary.lowercase().contains(lower) ||
                            article.keywords.lowercase().contains(lower)
                    }
                }
            }

            val total = allResults.size
            val offset = (page - 1) * size
            val paged = allResults.drop(offset).take(size)

            call.respond(ArticleListResponse(
                articles = paged.map { it.toDto() },
                total = total,
                page = page,
                size = size,
            ))
        }

        get("/{id}") {
            val db = call.application.koinGet<LumenDatabase>()
            val id = call.parameters["id"]?.toLongOrNull()
                ?: throw IllegalArgumentException("Invalid article ID")
            val article = db.articleBox.get(id)
                ?: throw NotFoundException("Article not found: $id")
            call.respond(article.toDto())
        }
    }
}
