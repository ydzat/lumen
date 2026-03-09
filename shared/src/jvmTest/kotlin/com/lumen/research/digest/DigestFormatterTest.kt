package com.lumen.research.digest

import com.lumen.core.database.entities.Digest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

class DigestFormatterTest {

    private val formatter = DigestFormatter()

    private fun makeDigest(
        title: String = "AI Research Digest",
        date: String = "2026-03-07",
        content: String = "Today's research focused on transformer architectures.",
        sourceBreakdown: String = "",
        projectSections: String = "",
        sparks: String = "",
    ): Digest {
        return Digest(
            title = title,
            date = date,
            content = content,
            sourceBreakdown = sourceBreakdown,
            projectSections = projectSections,
            sparks = sparks,
            createdAt = System.currentTimeMillis(),
        )
    }

    @Test
    fun format_producesStructuredOutput() {
        val breakdown = Json.encodeToString(listOf(
            DigestGenerator.SourceBreakdownEntry("arXiv CS.AI", 5, "Paper Title"),
            DigestGenerator.SourceBreakdownEntry("Hacker News", 3, "Post Title"),
        ))
        val digest = makeDigest(sourceBreakdown = breakdown)
        val output = formatter.format(digest)

        assertTrue(output.contains("# AI Research Digest"))
        assertTrue(output.contains("Date: 2026-03-07"))
        assertTrue(output.contains("## Overview"))
        assertTrue(output.contains("transformer architectures"))
        assertTrue(output.contains("## Source Breakdown"))
        assertTrue(output.contains("arXiv CS.AI: 5 article(s)"))
        assertTrue(output.contains("\"Paper Title\""))
        assertTrue(output.contains("Hacker News: 3 article(s)"))
    }

    @Test
    fun formatCompact_producesSingleParagraph() {
        val digest = makeDigest()
        val output = formatter.formatCompact(digest)

        assertTrue(output.startsWith("AI Research Digest:"))
        assertTrue(output.contains("transformer architectures"))
        assertTrue(!output.contains("\n\n"))
    }

    @Test
    fun format_withEmptySourceBreakdown_handlesGracefully() {
        val digest = makeDigest(sourceBreakdown = "")
        val output = formatter.format(digest)

        assertTrue(output.contains("# AI Research Digest"))
        assertTrue(output.contains("## Overview"))
        assertTrue(!output.contains("## Source Breakdown"))
    }

    @Test
    fun format_withEmptyContent_skipsOverview() {
        val digest = makeDigest(content = "")
        val output = formatter.format(digest)

        assertTrue(output.contains("# AI Research Digest"))
        assertTrue(!output.contains("## Overview"))
    }

    @Test
    fun formatCompact_withMultiParagraphContent_usesFirstParagraph() {
        val digest = makeDigest(content = "First paragraph.\n\nSecond paragraph.\n\nThird paragraph.")
        val output = formatter.formatCompact(digest)

        assertTrue(output.contains("First paragraph."))
        assertTrue(!output.contains("Second paragraph."))
    }

    @Test
    fun format_withProjectSections_rendersProjectHeaders() {
        val sections = Json.encodeToString(listOf(
            DigestGenerator.ProjectSection(projectId = 1, projectName = "AI Research", highlights = "- Paper A: Summary A", articleCount = 2),
            DigestGenerator.ProjectSection(projectId = 2, projectName = "HPC", highlights = "- Paper B: Summary B", articleCount = 1),
        ))
        val digest = makeDigest(projectSections = sections)
        val output = formatter.format(digest)

        assertTrue(output.contains("## Sections"))
        assertTrue(output.contains("### AI Research (2 articles)"))
        assertTrue(output.contains("- Paper A: Summary A"))
        assertTrue(output.contains("### HPC (1 article)"))
        assertTrue(output.contains("- Paper B: Summary B"))
    }

    @Test
    fun format_withSparks_rendersSparksSection() {
        val sparks = Json.encodeToString(listOf(
            DigestGenerator.SparkSection("Cross Insight", "AI meets HPC", listOf("parallel", "scheduling")),
        ))
        val digest = makeDigest(sparks = sparks)
        val output = formatter.format(digest)

        assertTrue(output.contains("## Spark Insights"))
        assertTrue(output.contains("**Cross Insight**"))
        assertTrue(output.contains("AI meets HPC"))
        assertTrue(output.contains("[parallel, scheduling]"))
    }

    @Test
    fun format_withBothSectionsAndSparks_rendersAll() {
        val sections = Json.encodeToString(listOf(
            DigestGenerator.ProjectSection(projectId = 1, projectName = "AI Research", highlights = "- Paper A: Summary", articleCount = 1),
        ))
        val sparks = Json.encodeToString(listOf(
            DigestGenerator.SparkSection("Insight", "Description", listOf("kw1")),
        ))
        val breakdown = Json.encodeToString(listOf(
            DigestGenerator.SourceBreakdownEntry("arXiv", 1, "Paper A"),
        ))
        val digest = makeDigest(
            projectSections = sections,
            sparks = sparks,
            sourceBreakdown = breakdown,
        )
        val output = formatter.format(digest)

        assertTrue(output.contains("## Overview"))
        assertTrue(output.contains("## Sections"))
        assertTrue(output.contains("## Spark Insights"))
        assertTrue(output.contains("## Source Breakdown"))
        val overviewPos = output.indexOf("## Overview")
        val sectionsPos = output.indexOf("## Sections")
        val sparksPos = output.indexOf("## Spark Insights")
        val breakdownPos = output.indexOf("## Source Breakdown")
        assertTrue(overviewPos < sectionsPos)
        assertTrue(sectionsPos < sparksPos)
        assertTrue(sparksPos < breakdownPos)
    }

    @Test
    fun format_withEmptyProjectSections_skipsSection() {
        val digest = makeDigest(projectSections = "")
        val output = formatter.format(digest)

        assertTrue(!output.contains("## Sections"))
    }

    @Test
    fun format_withEmptySparks_skipsSection() {
        val digest = makeDigest(sparks = "")
        val output = formatter.format(digest)

        assertTrue(!output.contains("## Spark Insights"))
    }
}
