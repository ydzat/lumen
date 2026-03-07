package com.lumen.server.routes

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Digest_
import com.lumen.research.collector.CollectorManager
import com.lumen.research.digest.DigestGenerator
import com.lumen.server.dto.AnalyzeResponse
import com.lumen.server.dto.TrendsDigestEntry
import com.lumen.server.dto.TrendsResponse
import com.lumen.server.dto.toDto
import com.lumen.server.notification.NtfyNotifier
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.get as koinGet

private val DATE_PATTERN = Regex("""\d{4}-\d{2}-\d{2}""")
private const val DEFAULT_TRENDS_DAYS = 7
private const val MAX_TRENDS_DAYS = 90

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

        get("/trends") {
            val db = call.application.koinGet<LumenDatabase>()
            val days = call.request.queryParameters["days"]?.toIntOrNull() ?: DEFAULT_TRENDS_DAYS
            if (days < 1 || days > MAX_TRENDS_DAYS) {
                throw IllegalArgumentException("days must be between 1 and $MAX_TRENDS_DAYS")
            }
            val since = System.currentTimeMillis() - days.toLong() * 24 * 60 * 60 * 1000
            val digests = db.digestBox.query()
                .greater(Digest_.createdAt, since)
                .orderDesc(Digest_.createdAt)
                .build()
                .use { it.find() }
            call.respond(TrendsResponse(
                days = days,
                digests = digests.map { d ->
                    TrendsDigestEntry(
                        date = d.date,
                        title = d.title,
                        content = d.content,
                        projectId = d.projectId,
                    )
                },
            ))
        }
    }

    post("/analyze/manual") {
        val collectorManager = call.application.koinGet<CollectorManager>()
        val notifier = call.application.koinGet<NtfyNotifier>()
        val result = collectorManager.runPipeline()

        result.digest?.let { notifier.notifyDigest(it) }
        notifier.notifyHighRelevanceArticles(result.scoredArticles)

        call.respond(AnalyzeResponse(
            fetched = result.fetched,
            analyzed = result.analyzed,
            scored = result.scored,
            digestId = result.digest?.id ?: 0L,
        ))
    }
}
