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
