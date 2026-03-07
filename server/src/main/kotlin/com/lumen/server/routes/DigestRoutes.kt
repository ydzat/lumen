package com.lumen.server.routes

import com.lumen.research.collector.CollectorManager
import com.lumen.research.digest.DigestGenerator
import com.lumen.server.dto.AnalyzeResponse
import com.lumen.server.dto.toDto
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.get as koinGet

private val DATE_PATTERN = Regex("""\d{4}-\d{2}-\d{2}""")

fun Route.digestRoutes() {
    route("/digest") {
        get("/daily") {
            val digestGenerator = call.application.koinGet<DigestGenerator>()
            val date = call.request.queryParameters["date"]
                ?: throw IllegalArgumentException("Missing required parameter: date")
            if (!date.matches(DATE_PATTERN)) {
                throw IllegalArgumentException("Invalid date format: $date (expected YYYY-MM-DD)")
            }
            val projectId = call.request.queryParameters["projectId"]?.toLongOrNull() ?: 0L
            val digest = digestGenerator.generate(date, projectId)
            call.respond(digest.toDto())
        }
    }

    post("/analyze/manual") {
        val collectorManager = call.application.koinGet<CollectorManager>()
        val result = collectorManager.runPipeline()
        call.respond(AnalyzeResponse(
            fetched = result.fetched,
            analyzed = result.analyzed,
            scored = result.scored,
            digestId = result.digest?.id ?: 0L,
        ))
    }
}
