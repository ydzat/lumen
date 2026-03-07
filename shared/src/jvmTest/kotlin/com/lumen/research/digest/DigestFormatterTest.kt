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
    ): Digest {
        return Digest(
            title = title,
            date = date,
            content = content,
            sourceBreakdown = sourceBreakdown,
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
        assertTrue(output.contains("## Highlights"))
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
        assertTrue(output.contains("## Highlights"))
        assertTrue(!output.contains("## Source Breakdown"))
    }

    @Test
    fun format_withEmptyContent_skipsHighlights() {
        val digest = makeDigest(content = "")
        val output = formatter.format(digest)

        assertTrue(output.contains("# AI Research Digest"))
        assertTrue(!output.contains("## Highlights"))
    }

    @Test
    fun formatCompact_withMultiParagraphContent_usesFirstParagraph() {
        val digest = makeDigest(content = "First paragraph.\n\nSecond paragraph.\n\nThird paragraph.")
        val output = formatter.formatCompact(digest)

        assertTrue(output.contains("First paragraph."))
        assertTrue(!output.contains("Second paragraph."))
    }
}
