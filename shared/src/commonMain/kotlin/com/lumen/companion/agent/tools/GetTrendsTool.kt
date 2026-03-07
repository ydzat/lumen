package com.lumen.companion.agent.tools

import ai.koog.agents.core.tools.SimpleTool
import com.lumen.core.database.LumenDatabase
import kotlinx.serialization.Serializable

@Serializable
data class GetTrendsArgs(
    val days: Int = 7,
)

class GetTrendsTool(
    private val db: LumenDatabase,
) : SimpleTool<GetTrendsArgs>(
    GetTrendsArgs.serializer(),
    "get_trends",
    "Summarize recent research trends from stored digests. Defaults to the last 7 days.",
) {
    override suspend fun execute(args: GetTrendsArgs): String {
        val cutoff = System.currentTimeMillis() - args.days.coerceAtLeast(1) * 86_400_000L
        val recentDigests = db.digestBox.all
            .filter { it.createdAt >= cutoff }
            .sortedByDescending { it.createdAt }

        if (recentDigests.isEmpty()) {
            return "No digests found in the last ${args.days} day(s)."
        }

        return buildString {
            appendLine("Research trends from the last ${args.days} day(s) (${recentDigests.size} digest(s)):")
            appendLine()
            for (digest in recentDigests) {
                appendLine("### ${digest.date}: ${digest.title}")
                if (digest.content.isNotBlank()) {
                    appendLine(digest.content.take(MAX_CONTENT_PER_DIGEST))
                    if (digest.content.length > MAX_CONTENT_PER_DIGEST) {
                        appendLine("...")
                    }
                }
                appendLine()
            }
        }.trim()
    }

    private companion object {
        const val MAX_CONTENT_PER_DIGEST = 500
    }
}
