package com.lumen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Renders clean HTML (from Readability4J) as Compose elements.
 * Handles the subset of tags that Readability4J produces:
 * h1-h6, p, a, strong/b, em/i, ul/ol/li, blockquote, pre/code, br, hr, img, figure, table.
 *
 * When HTML contains <math> MathML elements (e.g. arXiv papers), delegates to
 * a platform WebView with MathJax for proper mathematical typesetting.
 * SVG elements are rendered as their text content.
 * Only script and style elements are skipped.
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
                                    .firstOrNull()?.let { safeOpenUri(uriHandler, it.item) }
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }

                is HtmlBlock.Image -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AsyncImage(
                            model = block.src,
                            contentDescription = block.alt,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                                .clip(RoundedCornerShape(6.dp)),
                        )
                        if (block.caption.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = block.caption,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                is HtmlBlock.Table -> {
                    val borderColor = MaterialTheme.colorScheme.outlineVariant
                    val headerBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    val colCount = maxOf(
                        block.headerRow.size,
                        block.rows.maxOfOrNull { it.size } ?: 0,
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        if (block.caption.isNotBlank()) {
                            Text(
                                text = block.caption,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(0.5.dp, borderColor, RoundedCornerShape(4.dp))
                                .clip(RoundedCornerShape(4.dp)),
                        ) {
                            if (block.headerRow.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min)
                                        .background(headerBg),
                                ) {
                                    block.headerRow.forEachIndexed { idx, cell ->
                                        val annotated = buildStyledText(cell, linkColor)
                                        Text(
                                            text = annotated,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                        )
                                        if (idx < colCount - 1) {
                                            VerticalDivider(borderColor)
                                        }
                                    }
                                }
                                HorizontalDivider(color = borderColor)
                            }
                            block.rows.forEachIndexed { rowIdx, row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min),
                                ) {
                                    for (colIdx in 0 until colCount) {
                                        val cell = row.getOrNull(colIdx)
                                        if (cell != null) {
                                            val annotated = buildStyledText(cell, linkColor)
                                            Text(
                                                text = annotated,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                            )
                                        } else {
                                            Spacer(Modifier.weight(1f))
                                        }
                                        if (colIdx < colCount - 1) {
                                            VerticalDivider(borderColor)
                                        }
                                    }
                                }
                                if (rowIdx < block.rows.size - 1) {
                                    HorizontalDivider(color = borderColor)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
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
                                            .firstOrNull()?.let { safeOpenUri(uriHandler, it.item) }
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
    data class Image(val src: String, val alt: String, val caption: String = "") : HtmlBlock()
    data class Table(
        val caption: String,
        val headerRow: List<List<InlineElement>>,
        val rows: List<List<List<InlineElement>>>,
    ) : HtmlBlock()
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
    """<(h[1-6]|p|div|li|blockquote|pre|hr|ul|ol|figure|figcaption|table|img|nav|footer|header|section|article)[^>]*>""",
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

// Figure with optional caption: <figure><img ...><figcaption>...</figcaption></figure>
private val FIGURE_PATTERN = Regex(
    """<figure[^>]*>(.*?)</figure>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

// Standalone <img> tags (self-closing or not)
private val IMG_PATTERN = Regex(
    """<img\s[^>]*>""",
    RegexOption.IGNORE_CASE,
)

private val IMG_SRC_PATTERN = Regex("""src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
private val IMG_ALT_PATTERN = Regex("""alt\s*=\s*["']([^"']*?)["']""", RegexOption.IGNORE_CASE)
private val FIGCAPTION_PATTERN = Regex(
    """<figcaption[^>]*>(.*?)</figcaption>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

// Table parsing
private val TABLE_PATTERN = Regex(
    """<table[^>]*>(.*?)</table>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val TABLE_ROW_PATTERN = Regex(
    """<tr[^>]*>(.*?)</tr>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val TABLE_HEADER_CELL_PATTERN = Regex(
    """<th[^>]*>(.*?)</th>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val TABLE_DATA_CELL_PATTERN = Regex(
    """<t[dh][^>]*>(.*?)</t[dh]>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val TABLE_CAPTION_PATTERN = Regex(
    """<caption[^>]*>(.*?)</caption>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

// Tags to skip entirely — only non-content elements
private val SKIP_PATTERN = Regex(
    """<(script|style)[^>]*>.*?</\1>|<(br)[^>]*/?>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

// Math: prefer alttext (LaTeX) with conversion, fall back to MathML text extraction
private val MATH_WITH_ALTTEXT_PATTERN = Regex(
    """<math[^>]*?\balttext\s*=\s*"([^"]*?)"[^>]*>.*?</math>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val MATH_PATTERN = Regex(
    """<math[^>]*>(.*?)</math>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
// MathML <annotation> contains raw LaTeX source — must be removed before stripTags
private val ANNOTATION_PATTERN = Regex(
    """<annotation[^>]*>.*?</annotation>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

// SVG: extract text content (labels, annotations)
private val SVG_PATTERN = Regex(
    """<svg[^>]*>(.*?)</svg>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

internal fun parseHtmlToBlocks(html: String): List<HtmlBlock> {
    if (html.isBlank()) return emptyList()

    val blocks = mutableListOf<HtmlBlock>()
    // Work through the HTML sequentially, matching block-level elements
    var remaining = html.trim()

    // Pre-clean: remove non-content elements (script, style)
    remaining = SKIP_PATTERN.replace(remaining, "")
    // Replace math: prefer alttext (LaTeX source) converted to readable text
    remaining = MATH_WITH_ALTTEXT_PATTERN.replace(remaining) { match ->
        latexToText(decodeEntities(match.groupValues[1]))
    }
    // Remaining math without alttext: extract Unicode text from MathML
    remaining = MATH_PATTERN.replace(remaining) { match ->
        val mathContent = ANNOTATION_PATTERN.replace(match.groupValues[1], "")
        stripTags(mathContent)
            .replace(WHITESPACE_COLLAPSE, "")
            .replace("\u200B", "")
    }
    // Replace SVG with extracted text content (collapse whitespace from XML structure)
    remaining = SVG_PATTERN.replace(remaining) { match ->
        stripTags(match.groupValues[1]).replace(WHITESPACE_COLLAPSE, " ").trim()
    }
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
    for (match in TABLE_PATTERN.findAll(remaining)) {
        allMatches.add(Triple(match.range, "table", match))
    }
    for (match in FIGURE_PATTERN.findAll(remaining)) {
        allMatches.add(Triple(match.range, "figure", match))
    }
    for (match in PARAGRAPH_PATTERN.findAll(remaining)) {
        allMatches.add(Triple(match.range, "paragraph", match))
    }
    for (match in IMG_PATTERN.findAll(remaining)) {
        allMatches.add(Triple(match.range, "img", match))
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
            "table" -> {
                val tableHtml = match.groupValues[1]
                val caption = TABLE_CAPTION_PATTERN.find(tableHtml)?.let {
                    stripTags(it.groupValues[1]).trim()
                } ?: ""
                val tableRows = TABLE_ROW_PATTERN.findAll(tableHtml).toList()
                val headerRow = mutableListOf<List<InlineElement>>()
                val dataRows = mutableListOf<List<List<InlineElement>>>()
                for (row in tableRows) {
                    val rowHtml = row.groupValues[1]
                    val headerCells = TABLE_HEADER_CELL_PATTERN.findAll(rowHtml).toList()
                    if (headerCells.isNotEmpty() && headerRow.isEmpty()) {
                        headerRow.addAll(headerCells.map { parseInlineElements(it.groupValues[1].trim()) })
                    } else {
                        val cells = TABLE_DATA_CELL_PATTERN.findAll(rowHtml).map {
                            parseInlineElements(it.groupValues[1].trim())
                        }.toList()
                        if (cells.isNotEmpty()) {
                            dataRows.add(cells)
                        }
                    }
                }
                // Detect equation tables: single row, no header, mostly empty cells
                // (arXiv wraps display equations in <table> with padding cells)
                if (headerRow.isEmpty() && dataRows.size == 1) {
                    val cells = dataRows[0]
                    val nonEmpty = cells.filter { inlines -> inlines.any { it is InlineElement.Text && it.text.isNotBlank() || it !is InlineElement.Text } }
                    val cellTexts = cells.map { inlines -> inlines.filterIsInstance<InlineElement.Text>().joinToString("") { it.text }.trim() }
                    if (nonEmpty.size <= 2 && cells.size >= 3 &&
                        cellTexts.count { it.isBlank() } >= cells.size / 2
                    ) {
                        val merged = nonEmpty.flatten()
                        if (merged.isNotEmpty()) {
                            blocks.add(HtmlBlock.Paragraph(merged))
                        }
                        continue
                    }
                }
                if (headerRow.isNotEmpty() || dataRows.isNotEmpty()) {
                    blocks.add(HtmlBlock.Table(caption, headerRow, dataRows))
                }
            }
            "figure" -> {
                val figureHtml = match.groupValues[1]
                val figCaption = FIGCAPTION_PATTERN.find(figureHtml)?.let {
                    stripTags(it.groupValues[1]).trim()
                } ?: ""

                // Check for table inside figure (arXiv wraps tables in <figure>)
                val tableMatch = TABLE_PATTERN.find(figureHtml)
                val imgMatch = IMG_PATTERN.find(figureHtml)

                if (tableMatch != null) {
                    val tableHtml = tableMatch.groupValues[1]
                    val tableCaption = TABLE_CAPTION_PATTERN.find(tableHtml)?.let {
                        stripTags(it.groupValues[1]).trim()
                    } ?: figCaption
                    val tableRows = TABLE_ROW_PATTERN.findAll(tableHtml).toList()
                    val headerRow = mutableListOf<List<InlineElement>>()
                    val dataRows = mutableListOf<List<List<InlineElement>>>()
                    for (row in tableRows) {
                        val rowHtml = row.groupValues[1]
                        val headerCells = TABLE_HEADER_CELL_PATTERN.findAll(rowHtml).toList()
                        if (headerCells.isNotEmpty() && headerRow.isEmpty()) {
                            headerRow.addAll(headerCells.map { parseInlineElements(it.groupValues[1].trim()) })
                        } else {
                            val cells = TABLE_DATA_CELL_PATTERN.findAll(rowHtml).map {
                                parseInlineElements(it.groupValues[1].trim())
                            }.toList()
                            if (cells.isNotEmpty()) {
                                dataRows.add(cells)
                            }
                        }
                    }
                    if (headerRow.isNotEmpty() || dataRows.isNotEmpty()) {
                        blocks.add(HtmlBlock.Table(tableCaption, headerRow, dataRows))
                    }
                } else if (imgMatch != null) {
                    val src = IMG_SRC_PATTERN.find(imgMatch.value)?.groupValues?.get(1) ?: ""
                    val alt = IMG_ALT_PATTERN.find(imgMatch.value)?.groupValues?.get(1) ?: ""
                    if (src.isNotBlank()) {
                        blocks.add(HtmlBlock.Image(src, alt, figCaption))
                    }
                }
            }
            "img" -> {
                val src = IMG_SRC_PATTERN.find(match.value)?.groupValues?.get(1) ?: ""
                val alt = IMG_ALT_PATTERN.find(match.value)?.groupValues?.get(1) ?: ""
                if (src.isNotBlank()) {
                    blocks.add(HtmlBlock.Image(src, alt))
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
    """<(strong|b|em|i|code|a|span)([^>]*)>(.*?)</\1>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

private val HREF_PATTERN = Regex("""href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

internal fun parseInlineElements(html: String): List<InlineElement> {
    val elements = mutableListOf<InlineElement>()
    var lastEnd = 0

    for (match in INLINE_PATTERN.findAll(html)) {
        // Text before this tag — collapse whitespace (HTML rendering rule)
        if (match.range.first > lastEnd) {
            val before = collapseWhitespace(decodeEntities(stripTags(html.substring(lastEnd, match.range.first))))
            if (before.isNotBlank()) {
                elements.add(InlineElement.Text(before))
            }
        }

        val tag = match.groupValues[1].lowercase()
        val attrs = match.groupValues[2]
        val content = collapseWhitespace(decodeEntities(stripTags(match.groupValues[3])))

        when (tag) {
            "strong", "b" -> elements.add(InlineElement.Bold(content))
            "em", "i" -> elements.add(InlineElement.Italic(content))
            "code" -> elements.add(InlineElement.Code(content))
            "a" -> {
                val href = HREF_PATTERN.find(attrs)?.groupValues?.get(1) ?: ""
                elements.add(InlineElement.Link(content, href))
            }
            "span" -> {
                if (content.isNotBlank()) elements.add(InlineElement.Text(content))
            }
        }

        lastEnd = match.range.last + 1
    }

    // Remaining text after last tag
    if (lastEnd < html.length) {
        val after = collapseWhitespace(decodeEntities(stripTags(html.substring(lastEnd))))
        if (after.isNotBlank()) {
            elements.add(InlineElement.Text(after))
        }
    }

    // If nothing was parsed, treat whole input as plain text
    if (elements.isEmpty()) {
        val text = collapseWhitespace(decodeEntities(stripTags(html)))
        if (text.isNotBlank()) {
            elements.add(InlineElement.Text(text))
        }
    }

    return elements
}

// --- Rendering helpers ---

@Composable
private fun VerticalDivider(color: androidx.compose.ui.graphics.Color) {
    Spacer(
        Modifier
            .fillMaxHeight()
            .width(0.5.dp)
            .background(color),
    )
}

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

private val WHITESPACE_COLLAPSE = Regex("""\s+""")

private fun collapseWhitespace(text: String): String {
    return text.replace(WHITESPACE_COLLAPSE, " ")
}

private val LATEX_COMMANDS = mapOf(
    "\\pi" to "π", "\\Pi" to "Π",
    "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\Gamma" to "Γ",
    "\\delta" to "δ", "\\Delta" to "Δ", "\\epsilon" to "ε", "\\varepsilon" to "ε",
    "\\zeta" to "ζ", "\\eta" to "η", "\\theta" to "θ", "\\Theta" to "Θ",
    "\\lambda" to "λ", "\\Lambda" to "Λ", "\\mu" to "μ", "\\nu" to "ν",
    "\\xi" to "ξ", "\\Xi" to "Ξ", "\\rho" to "ρ", "\\sigma" to "σ", "\\Sigma" to "Σ",
    "\\tau" to "τ", "\\phi" to "φ", "\\Phi" to "Φ", "\\chi" to "χ",
    "\\psi" to "ψ", "\\Psi" to "Ψ", "\\omega" to "ω", "\\Omega" to "Ω",
    "\\times" to "×", "\\sim" to "~", "\\approx" to "≈",
    "\\pm" to "±", "\\mp" to "∓", "\\cdot" to "·", "\\circ" to "°",
    "\\leq" to "≤", "\\geq" to "≥", "\\neq" to "≠", "\\equiv" to "≡",
    "\\ll" to "≪", "\\gg" to "≫",
    "\\infty" to "∞", "\\ell" to "ℓ", "\\partial" to "∂", "\\nabla" to "∇",
    "\\forall" to "∀", "\\exists" to "∃", "\\in" to "∈", "\\notin" to "∉",
    "\\subset" to "⊂", "\\supset" to "⊃", "\\cup" to "∪", "\\cap" to "∩",
    "\\sum" to "Σ", "\\prod" to "Π", "\\int" to "∫",
    "\\rightarrow" to "→", "\\leftarrow" to "←", "\\Rightarrow" to "⇒",
    "\\langle" to "⟨", "\\rangle" to "⟩",
    "\\ldots" to "…", "\\dots" to "…", "\\cdots" to "···",
    "\\mathdollar" to "$",
    "\\left" to "", "\\right" to "", "\\big" to "", "\\Big" to "",
    "\\," to "", "\\;" to " ", "\\!" to "", "\\quad" to " ", "\\qquad" to "  ",
)

// Patterns for LaTeX commands with braced arguments
private val LATEX_BRACE_CMD = Regex("""\\(?:mathbb|mathbf|mathcal|mathrm|mathit|mathsf|text|textit|textbf|boldsymbol|operatorname)\{([^}]*)}""")
private val LATEX_FRAC = Regex("""\\frac\{([^}]*?)}\{([^}]*?)}""")
private val LATEX_SQRT = Regex("""\\sqrt\{([^}]*?)}""")
private val LATEX_UNKNOWN_CMD = Regex("""\\[a-zA-Z]+""")

private fun latexToText(latex: String): String {
    var result = latex

    // Replace \frac{a}{b} → a/b
    result = LATEX_FRAC.replace(result) { "${it.groupValues[1]}/${it.groupValues[2]}" }

    // Replace \sqrt{x} → √x
    result = LATEX_SQRT.replace(result) { "√${it.groupValues[1]}" }

    // Replace \mathbb{E}, \mathbf{s}, \text{...}, etc. → just the content
    result = LATEX_BRACE_CMD.replace(result) { it.groupValues[1] }

    // Replace known commands (sorted by length descending to match longer first)
    for ((cmd, replacement) in LATEX_COMMANDS.entries.sortedByDescending { it.key.length }) {
        result = result.replace(cmd, replacement)
    }

    // Remove remaining unknown commands
    result = LATEX_UNKNOWN_CMD.replace(result, "")

    // Remove grouping braces
    result = result.replace("{", "").replace("}", "")

    // Clean up: ^° → ° (degree symbol is not a superscript in text)
    result = result.replace("^°", "°")

    // Clean up whitespace
    result = result.replace(WHITESPACE_COLLAPSE, " ").trim()

    return result
}
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

private fun safeOpenUri(uriHandler: UriHandler, uri: String) {
    if (uri.isBlank() || uri.startsWith("#")) return
    try {
        uriHandler.openUri(uri)
    } catch (_: Exception) {
        // Ignore malformed or unsupported URIs
    }
}

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
