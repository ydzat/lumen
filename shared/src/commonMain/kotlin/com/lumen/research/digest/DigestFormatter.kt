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
            sb.appendLine("## Overview")
            sb.appendLine()
            sb.appendLine(digest.content)
            sb.appendLine()
        }

        val sections = parseProjectSections(digest.projectSections)
        if (sections.isNotEmpty()) {
            sb.appendLine("## Sections")
            sb.appendLine()
            for (section in sections) {
                val articleLabel = if (section.articleCount == 1) "article" else "articles"
                sb.appendLine("### ${section.projectName} (${section.articleCount} $articleLabel)")
                sb.appendLine()
                if (section.highlights.isNotBlank()) {
                    sb.appendLine("**Key Findings:**")
                    sb.appendLine(section.highlights)
                    sb.appendLine()
                }
                if (section.trends.isNotBlank()) {
                    sb.appendLine("**Trends:**")
                    sb.appendLine(section.trends)
                    sb.appendLine()
                }
                if (section.insight.isNotBlank()) {
                    sb.appendLine("**Insight:** ${section.insight}")
                    sb.appendLine()
                }
            }
        }

        val sparks = parseSparkSections(digest.sparks)
        if (sparks.isNotEmpty()) {
            sb.appendLine("## Spark Insights")
            sb.appendLine()
            for (spark in sparks) {
                val keywords = if (spark.relatedKeywords.isNotEmpty()) {
                    " [${spark.relatedKeywords.joinToString(", ")}]"
                } else ""
                sb.appendLine("- **${spark.title}**: ${spark.description}$keywords")
            }
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

    fun parseSourceBreakdown(raw: String): List<DigestGenerator.SourceBreakdownEntry> {
        if (raw.isBlank()) return emptyList()
        return try {
            json.decodeFromString<List<DigestGenerator.SourceBreakdownEntry>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parseProjectSections(raw: String): List<DigestGenerator.ProjectSection> {
        if (raw.isBlank()) return emptyList()
        return try {
            json.decodeFromString<List<DigestGenerator.ProjectSection>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parseSparkSections(raw: String): List<DigestGenerator.SparkSection> {
        if (raw.isBlank()) return emptyList()
        return try {
            json.decodeFromString<List<DigestGenerator.SparkSection>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
