package com.lumen.core.util

fun extractJsonObject(text: String): String {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start == -1 || end == -1 || end <= start) return "{}"
    return text.substring(start, end + 1)
}
