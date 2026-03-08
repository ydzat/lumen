package com.lumen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Renders clean HTML (from Readability4J) as Compose elements.
 * Handles the subset of tags that Readability4J produces:
 * h1-h6, p, a, strong/b, em/i, ul/ol/li, blockquote, pre/code, br, hr.
 * Skips img, figure, script, style, table elements.
 */
@Composable
fun HtmlContentRenderer(
    html: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(html) { parseHtmlToBlocks(html) }
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val quoteColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier) {
        for (block in blocks) {
            when (block) {
                is HtmlBlock.Heading -> {
                    if (block != blocks.first()) Spacer(Modifier.height(8.dp))
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        3 -> MaterialTheme.typography.titleSmall
                        else -> MaterialTheme.typography.bodyLarge
                    }
                    Text(
                        text = block.text,
                        style = style,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                }

                is HtmlBlock.Paragraph -> {
                    val annotated = buildStyledText(block.inlineElements, linkColor)
                    if (annotated.text.isNotBlank()) {
                        ClickableText(
                            text = annotated,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            onClick = { offset ->
                                annotated.getStringAnnotations("URL", offset, offset)
                                    .firstOrNull()?.let { uriHandler.openUri(it.item) }
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }

                is HtmlBlock.ListBlock -> {
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        block.items.forEachIndexed { index, item ->
                            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                val bullet = if (block.ordered) "${index + 1}." else "\u2022"
                                Text(
                                    text = bullet,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.width(20.dp),
                                )
                                val annotated = buildStyledText(item, linkColor)
                                ClickableText(
                                    text = annotated,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                    onClick = { offset ->
                                        annotated.getStringAnnotations("URL", offset, offset)
                                            .firstOrNull()?.let { uriHandler.openUri(it.item) }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                is HtmlBlock.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Spacer(
                            Modifier
                                .width(3.dp)
                                .height(40.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(quoteColor.copy(alpha = 0.4f)),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = quoteColor,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }

                is HtmlBlock.CodeBlock -> {
                    Text(
                        text = block.code,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(codeBackground)
                            .padding(8.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                }

                is HtmlBlock.Divider -> {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

// --- Data model ---

internal sealed class HtmlBlock {
    data class Heading(val level: Int, val text: String) : HtmlBlock()
    data class Paragraph(val inlineElements: List<InlineElement>) : HtmlBlock()
    data class ListBlock(val ordered: Boolean, val items: List<List<InlineElement>>) : HtmlBlock()
    data class Blockquote(val text: String) : HtmlBlock()
    data class CodeBlock(val code: String) : HtmlBlock()
    data object Divider : HtmlBlock()
}

internal sealed class InlineElement {
    data class Text(val text: String) : InlineElement()
    data class Bold(val text: String) : InlineElement()
    data class Italic(val text: String) : InlineElement()
    data class Code(val text: String) : InlineElement()
    data class Link(val text: String, val url: String) : InlineElement()
}

// --- HTML parsing ---

private val BLOCK_TAG_PATTERN = Regex(
    """<(h[1-6]|p|div|li|blockquote|pre|hr|ul|ol|figure|figcaption|table|img|script|style|nav|footer|header)[^>]*>""",
    RegexOption.IGNORE_CASE,
)

private val HEADING_PATTERN = Regex(
    """<h([1-6])[^>]*>(.*?)</h\1>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

private val PARAGRAPH_PATTERN = Regex(
    """<p[^>]*>(.*?)</p>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

private val LIST_PATTERN = Regex(
    """<(ul|ol)[^>]*>(.*?)</\1>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

private val LIST_ITEM_PATTERN = Regex(
    """<li[^>]*>(.*?)</li>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

private val BLOCKQUOTE_PATTERN = Regex(
    """<blockquote[^>]*>(.*?)</blockquote>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

private val PRE_PATTERN = Regex(
    """<pre[^>]*>(.*?)</pre>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

private val HR_PATTERN = Regex("""<hr\s*/?>""", RegexOption.IGNORE_CASE)

private val DIV_PATTERN = Regex(
    """<div[^>]*>(.*?)</div>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

// Tags to skip entirely
private val SKIP_PATTERN = Regex(
    """<(figure|figcaption|img|script|style|nav|footer|header|table|thead|tbody|tr|td|th|svg|math)[^>]*>.*?</\1>|<(img|br|hr)[^>]*/?>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

internal fun parseHtmlToBlocks(html: String): List<HtmlBlock> {
    if (html.isBlank()) return emptyList()

    val blocks = mutableListOf<HtmlBlock>()
    // Work through the HTML sequentially, matching block-level elements
    var remaining = html.trim()

    // Pre-clean: remove skip-worthy elements
    remaining = SKIP_PATTERN.replace(remaining, "")

    // Extract blocks in document order using a position-based approach
    val allMatches = mutableListOf<Triple<IntRange, String, MatchResult>>()

    for (match in HEADING_PATTERN.findAll(remaining)) {
        allMatches.add(Triple(match.range, "heading", match))
    }
    for (match in PRE_PATTERN.findAll(remaining)) {
        allMatches.add(Triple(match.range, "pre", match))
    }
    for (match in LIST_PATTERN.findAll(remaining)) {
        allMatches.add(Triple(match.range, "list", match))
    }
    for (match in BLOCKQUOTE_PATTERN.findAll(remaining)) {
        allMatches.add(Triple(match.range, "blockquote", match))
    }
    for (match in PARAGRAPH_PATTERN.findAll(remaining)) {
        allMatches.add(Triple(match.range, "paragraph", match))
    }
    for (match in HR_PATTERN.findAll(remaining)) {
        allMatches.add(Triple(match.range, "hr", match))
    }

    // Sort by position, remove overlapping matches (keep earlier/outer ones)
    allMatches.sortBy { it.first.first }
    val filtered = mutableListOf<Triple<IntRange, String, MatchResult>>()
    var lastEnd = -1
    for (entry in allMatches) {
        if (entry.first.first > lastEnd) {
            filtered.add(entry)
            lastEnd = entry.first.last
        }
    }

    for ((_, type, match) in filtered) {
        when (type) {
            "heading" -> {
                val level = match.groupValues[1].toIntOrNull() ?: 2
                val text = stripTags(match.groupValues[2]).trim()
                if (text.isNotBlank()) {
                    blocks.add(HtmlBlock.Heading(level, text))
                }
            }
            "paragraph" -> {
                val innerHtml = match.groupValues[1].trim()
                if (innerHtml.isNotBlank()) {
                    val inlines = parseInlineElements(innerHtml)
                    if (inlines.any { it is InlineElement.Text && it.text.isNotBlank() || it !is InlineElement.Text }) {
                        blocks.add(HtmlBlock.Paragraph(inlines))
                    }
                }
            }
            "list" -> {
                val ordered = match.groupValues[1].equals("ol", ignoreCase = true)
                val items = LIST_ITEM_PATTERN.findAll(match.groupValues[2])
                    .map { parseInlineElements(it.groupValues[1].trim()) }
                    .toList()
                if (items.isNotEmpty()) {
                    blocks.add(HtmlBlock.ListBlock(ordered, items))
                }
            }
            "blockquote" -> {
                val text = stripTags(match.groupValues[1]).trim()
                if (text.isNotBlank()) {
                    blocks.add(HtmlBlock.Blockquote(text))
                }
            }
            "pre" -> {
                val code = stripTags(match.groupValues[1]).trim()
                if (code.isNotBlank()) {
                    blocks.add(HtmlBlock.CodeBlock(code))
                }
            }
            "hr" -> {
                blocks.add(HtmlBlock.Divider)
            }
        }
    }

    // If no block elements found, treat the whole thing as a paragraph
    if (blocks.isEmpty() && remaining.isNotBlank()) {
        val text = stripTags(remaining).trim()
        if (text.isNotBlank()) {
            blocks.add(HtmlBlock.Paragraph(listOf(InlineElement.Text(text))))
        }
    }

    return blocks
}

// --- Inline element parsing ---

private val INLINE_PATTERN = Regex(
    """<(strong|b|em|i|code|a)([^>]*)>(.*?)</\1>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

private val HREF_PATTERN = Regex("""href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

internal fun parseInlineElements(html: String): List<InlineElement> {
    val elements = mutableListOf<InlineElement>()
    var lastEnd = 0

    for (match in INLINE_PATTERN.findAll(html)) {
        // Text before this tag
        if (match.range.first > lastEnd) {
            val before = decodeEntities(stripTags(html.substring(lastEnd, match.range.first)))
            if (before.isNotBlank()) {
                elements.add(InlineElement.Text(before))
            }
        }

        val tag = match.groupValues[1].lowercase()
        val attrs = match.groupValues[2]
        val content = decodeEntities(stripTags(match.groupValues[3]))

        when (tag) {
            "strong", "b" -> elements.add(InlineElement.Bold(content))
            "em", "i" -> elements.add(InlineElement.Italic(content))
            "code" -> elements.add(InlineElement.Code(content))
            "a" -> {
                val href = HREF_PATTERN.find(attrs)?.groupValues?.get(1) ?: ""
                elements.add(InlineElement.Link(content, href))
            }
        }

        lastEnd = match.range.last + 1
    }

    // Remaining text after last tag
    if (lastEnd < html.length) {
        val after = decodeEntities(stripTags(html.substring(lastEnd)))
        if (after.isNotBlank()) {
            elements.add(InlineElement.Text(after))
        }
    }

    // If nothing was parsed, treat whole input as plain text
    if (elements.isEmpty()) {
        val text = decodeEntities(stripTags(html))
        if (text.isNotBlank()) {
            elements.add(InlineElement.Text(text))
        }
    }

    return elements
}

// --- Rendering helpers ---

@Composable
private fun buildStyledText(
    elements: List<InlineElement>,
    linkColor: androidx.compose.ui.graphics.Color,
): AnnotatedString {
    return buildAnnotatedString {
        for (element in elements) {
            when (element) {
                is InlineElement.Text -> append(element.text)
                is InlineElement.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(element.text)
                }
                is InlineElement.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(element.text)
                }
                is InlineElement.Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                    append(element.text)
                }
                is InlineElement.Link -> {
                    pushStringAnnotation("URL", element.url)
                    withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                        append(element.text)
                    }
                    pop()
                }
            }
        }
    }
}

// --- Utility ---

private val HTML_TAG_REGEX = Regex("<[^>]*>")
private val HTML_ENTITY_MAP = mapOf(
    "&amp;" to "&", "&lt;" to "<", "&gt;" to ">",
    "&quot;" to "\"", "&apos;" to "'", "&nbsp;" to " ",
    "&#39;" to "'", "&#x27;" to "'", "&#x2F;" to "/",
    "&mdash;" to "\u2014", "&ndash;" to "\u2013",
    "&lsquo;" to "\u2018", "&rsquo;" to "\u2019",
    "&ldquo;" to "\u201C", "&rdquo;" to "\u201D",
    "&bull;" to "\u2022", "&hellip;" to "\u2026",
)
private val HTML_NUMERIC_ENTITY = Regex("&#(\\d+);")
private val HTML_HEX_ENTITY = Regex("&#x([0-9a-fA-F]+);")

internal fun stripTags(html: String): String {
    return HTML_TAG_REGEX.replace(html, "")
}

internal fun decodeEntities(text: String): String {
    var result = text
    for ((entity, char) in HTML_ENTITY_MAP) {
        result = result.replace(entity, char)
    }
    result = HTML_NUMERIC_ENTITY.replace(result) {
        val code = it.groupValues[1].toIntOrNull()
        if (code != null) code.toChar().toString() else it.value
    }
    result = HTML_HEX_ENTITY.replace(result) {
        val code = it.groupValues[1].toIntOrNull(16)
        if (code != null) code.toChar().toString() else it.value
    }
    return result
}
