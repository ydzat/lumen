package com.lumen.research.collector

enum class PipelineStage {
    FETCHING,
    DEDUPLICATING,
    EMBEDDING,
    SCORING,
    ANALYZING,
    SPARKING,
    DIGESTING,
}

fun interface PipelineProgress {
    suspend fun onProgress(stage: PipelineStage, current: Int, total: Int)
}
