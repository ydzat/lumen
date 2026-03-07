package com.lumen.core.memory

import com.lumen.core.database.LumenDatabase
import com.lumen.core.util.extractJsonObject
import com.lumen.core.database.entities.MemoryEntry
import com.lumen.core.database.entities.MemoryEntry_
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class SubQueryResponse(
    @SerialName("sub_queries") val subQueries: List<String> = emptyList(),
)

class IntentRetriever(
    private val database: LumenDatabase,
    private val llmCall: LlmCall,
    private val embeddingClient: EmbeddingClient,
    private val maxSubQueries: Int = 5,
    private val shortQueryThreshold: Int = 10,
) {

    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        private val WHITESPACE = "\\s+".toRegex()
    }

    suspend fun retrieve(query: String, limit: Int): List<MemoryEntry> {
        val subQueries = decompose(query)
        val allResults = searchParallel(subQueries, limit)
        return mergeAndRank(allResults, limit)
    }

    internal suspend fun decompose(query: String): List<String> {
        val wordCount = query.trim().split(WHITESPACE).size
        if (wordCount <= shortQueryThreshold) {
            return listOf(query)
        }

        val systemPrompt = buildSystemPrompt()
        val userPrompt = buildUserPrompt(query)
        val response = llmCall.execute(systemPrompt, userPrompt)
        return parseSubQueries(response) ?: listOf(query)
    }

    private suspend fun searchParallel(
        subQueries: List<String>,
        limit: Int,
    ): List<Pair<MemoryEntry, Float>> {
        return coroutineScope {
            subQueries.map { subQuery ->
                async {
                    val embedding = embeddingClient.embed(subQuery)
                    val results = database.memoryEntryBox.query()
                        .nearestNeighbors(MemoryEntry_.embedding, embedding, limit)
                        .build()
                        .use { it.find() }
                    results.map { entry ->
                        entry to cosineSimilarity(embedding, entry.embedding)
                    }
                }
            }.awaitAll().flatten()
        }
    }

    private fun mergeAndRank(
        results: List<Pair<MemoryEntry, Float>>,
        limit: Int,
    ): List<MemoryEntry> {
        if (results.isEmpty()) return emptyList()

        return results
            .groupBy { it.first.id }
            .map { (_, entries) ->
                val entry = entries.first().first
                val maxSimilarity = entries.maxOf { it.second }
                val hitCount = entries.size
                Triple(entry, maxSimilarity, hitCount)
            }
            .sortedWith(
                compareByDescending<Triple<MemoryEntry, Float, Int>> { it.third }
                    .thenByDescending { it.second },
            )
            .take(limit)
            .map { it.first }
    }

    internal fun buildSystemPrompt(): String {
        return """
You are a query decomposition assistant. Given a user's memory recall query, break it down into multiple specific sub-queries that together cover the full intent.

## Rules

1. Generate 2-5 sub-queries that capture different aspects of the original query.
2. Each sub-query should be a short, focused search phrase.
3. Include the original query essence in at least one sub-query.
4. Do not generate redundant or overlapping sub-queries.

## Output Format

Return a JSON object only, with no other text:

```json
{"sub_queries": ["query1", "query2", "query3"]}
```
        """.trimIndent()
    }

    internal fun buildUserPrompt(query: String): String {
        return """
Original query: "$query"

Decompose this into specific sub-queries for memory retrieval.
        """.trimIndent()
    }

    internal fun parseSubQueries(response: String): List<String>? {
        val jsonText = extractJsonObject(response)
        return try {
            val parsed = json.decodeFromString<SubQueryResponse>(jsonText)
            val queries = parsed.subQueries.filter { it.isNotBlank() }
            if (queries.isEmpty()) null
            else queries.take(maxSubQueries)
        } catch (_: Exception) {
            null
        }
    }

}
