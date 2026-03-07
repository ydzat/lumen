package com.lumen.research

fun parseCsvSet(csv: String): Set<String> {
    if (csv.isBlank()) return emptySet()
    return csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}
