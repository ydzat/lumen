package com.lumen.research.collector

enum class SourceType {
    RSS,
    ARXIV_API,
    SEMANTIC_SCHOLAR,
    GITHUB_RELEASES;

    companion object {
        fun fromString(value: String): SourceType {
            return try {
                valueOf(value.uppercase())
            } catch (_: IllegalArgumentException) {
                RSS
            }
        }
    }
}
