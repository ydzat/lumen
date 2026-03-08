package com.lumen.ui

fun displaySourceType(type: String): String = when (type.uppercase()) {
    "ARXIV_API" -> "arXiv API"
    "SEMANTIC_SCHOLAR" -> "Semantic Scholar"
    "GITHUB_RELEASES" -> "GitHub Releases"
    "RSS" -> "RSS"
    else -> type
}

fun displaySourceTypeShort(type: String): String = when (type.uppercase()) {
    "ARXIV_API" -> "arXiv"
    "SEMANTIC_SCHOLAR" -> "Scholar"
    "GITHUB_RELEASES" -> "GitHub"
    "RSS" -> "RSS"
    else -> type
}

private val HTML_TAG_REGEX = Regex("<[^>]*>")
private val HTML_ENTITY_MAP = mapOf(
    "&amp;" to "&", "&lt;" to "<", "&gt;" to ">",
    "&quot;" to "\"", "&apos;" to "'", "&nbsp;" to " ",
    "&#39;" to "'", "&#x27;" to "'", "&#x2F;" to "/",
)
private val HTML_NUMERIC_ENTITY = Regex("&#(\\d+);")

fun stripHtml(html: String): String {
    if (html.isBlank()) return html
    var text = html
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</?p>", RegexOption.IGNORE_CASE), "\n")
    text = HTML_TAG_REGEX.replace(text, "")
    for ((entity, char) in HTML_ENTITY_MAP) {
        text = text.replace(entity, char)
    }
    text = HTML_NUMERIC_ENTITY.replace(text) {
        val code = it.groupValues[1].toIntOrNull()
        if (code != null) code.toChar().toString() else it.value
    }
    return text.replace(Regex("\n{3,}"), "\n\n").trim()
}
