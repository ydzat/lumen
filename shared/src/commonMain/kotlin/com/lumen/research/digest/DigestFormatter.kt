package com.lumen.research.digest

import com.lumen.core.database.entities.Digest
import kotlinx.serialization.json.Json

class DigestFormatter {

    private val json = Json { ignoreUnknownKeys = true }

    fun format(digest: Digest): String {
        val sb = StringBuilder()
        sb.appendLine("# ${digest.title}")
        sb.appendLine()
        sb.appendLine("Date: ${digest.date}")
        sb.appendLine()

        if (digest.content.isNotBlank()) {
            sb.appendLine("## Highlights")
            sb.appendLine()
            sb.appendLine(digest.content)
            sb.appendLine()
        }

        val breakdown = parseSourceBreakdown(digest.sourceBreakdown)
        if (breakdown.isNotEmpty()) {
            sb.appendLine("## Source Breakdown")
            sb.appendLine()
            for (entry in breakdown) {
                val topArticlePart = if (entry.topArticle.isNotBlank()) {
                    " — top: \"${entry.topArticle}\""
                } else ""
                sb.appendLine("- ${entry.source}: ${entry.count} article(s)$topArticlePart")
            }
            sb.appendLine()
        }

        return sb.toString().trimEnd() + "\n"
    }

    fun formatCompact(digest: Digest): String {
        val firstParagraph = digest.content
            .split("\n\n")
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?: digest.title

        return "${digest.title}: $firstParagraph"
    }

    private fun parseSourceBreakdown(raw: String): List<DigestGenerator.SourceBreakdownEntry> {
        if (raw.isBlank()) return emptyList()
        return try {
            json.decodeFromString<List<DigestGenerator.SourceBreakdownEntry>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
