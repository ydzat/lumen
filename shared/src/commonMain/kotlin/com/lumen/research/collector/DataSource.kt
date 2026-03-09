package com.lumen.research.collector

import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.Source

interface DataSource {
    val type: SourceType
    val displayName: String
    suspend fun fetch(sources: List<Source>, context: FetchContext): DataFetchResult
}

data class DataFetchResult(
    val articles: List<Article>,
    val errors: List<String>,
    val sourceType: SourceType,
    val failedSourceIds: Set<Long> = emptySet(),
)
