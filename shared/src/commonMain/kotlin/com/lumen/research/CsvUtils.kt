package com.lumen.research

fun parseCsvSet(csv: String): Set<String> {
    if (csv.isBlank()) return emptySet()
    return csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}

fun languageDisplayName(code: String): String {
    return when (code.lowercase()) {
        "zh" -> "Chinese (中文)"
        "en" -> "English"
        else -> "English"
    }
}
