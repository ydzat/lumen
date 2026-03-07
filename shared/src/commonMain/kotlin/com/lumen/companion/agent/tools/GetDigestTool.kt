package com.lumen.companion.agent.tools

import ai.koog.agents.core.tools.SimpleTool
import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Digest_
import io.objectbox.query.QueryBuilder
import com.lumen.core.util.formatEpochDate
import kotlinx.serialization.Serializable

@Serializable
data class GetDigestArgs(
    val date: String = "",
)

class GetDigestTool(
    private val db: LumenDatabase,
) : SimpleTool<GetDigestArgs>(
    GetDigestArgs.serializer(),
    "get_digest",
    "Retrieve the research digest for a specific date (YYYY-MM-DD). Defaults to today.",
) {
    override suspend fun execute(args: GetDigestArgs): String {
        val targetDate = args.date.ifBlank { formatEpochDate(System.currentTimeMillis()) }
        val digest = db.digestBox.query()
            .equal(Digest_.date, targetDate, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build()
            .use { it.findFirst() }
            ?: return "No digest found for $targetDate."

        return buildString {
            appendLine("**${digest.title}**")
            appendLine("Date: ${digest.date}")
            if (digest.content.isNotBlank()) {
                appendLine()
                append(digest.content)
            }
        }
    }
}
