package com.lumen.core.memory

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.MemoryEntry
import com.lumen.core.database.entities.MemoryEntry_
import kotlinx.serialization.json.Json

class SemanticSynthesizer(
    private val database: LumenDatabase,
    private val llmCall: LlmCall,
    private val similarityThreshold: Float = 0.85f,
    private val maxNeighbors: Int = 5,
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun synthesize(candidate: MemoryEntry): SynthesisResult {
        if (candidate.embedding.isEmpty()) return SynthesisResult.NoMatch

        val neighbors = database.memoryEntryBox.query()
            .nearestNeighbors(MemoryEntry_.embedding, candidate.embedding, maxNeighbors)
            .build()
            .use { it.find() }

        if (neighbors.isEmpty()) return SynthesisResult.NoMatch

        val bestMatch = neighbors
            .map { it to cosineSimilarity(candidate.embedding, it.embedding) }
            .filter { (_, similarity) -> similarity >= similarityThreshold }
            .maxByOrNull { (_, similarity) -> similarity }

        if (bestMatch == null) return SynthesisResult.NoMatch

        val (existing, similarity) = bestMatch
        val decision = askLlmForDecision(candidate.content, existing.content, similarity)

        return when (decision.action) {
            "merge", "update" -> executeMerge(candidate, existing, decision)
            else -> SynthesisResult.Kept
        }
    }

    internal suspend fun askLlmForDecision(
        newContent: String,
        existingContent: String,
        similarity: Float,
    ): MergeDecision {
        val systemPrompt = buildSystemPrompt()
        val userPrompt = buildUserPrompt(newContent, existingContent, similarity)
        val response = llmCall.execute(systemPrompt, userPrompt)
        return parseDecision(response)
    }

    internal fun buildSystemPrompt(): String {
        return """
You are a memory deduplication assistant. You compare two memory entries and decide whether they should be merged.

## Rules

1. **merge**: The two entries describe the same fact with different wording. Produce a single, comprehensive merged statement that preserves all details from both entries.
2. **update**: The new entry supersedes or refines the existing entry (e.g., corrects outdated information). Produce an updated statement.
3. **keep_both**: The entries are related but describe genuinely different facts. Keep both entries.

## Output Format

Return a JSON object only, with no other text:

```json
{
  "action": "merge|keep_both|update",
  "mergedContent": "Combined or updated statement (empty if keep_both)",
  "reason": "Brief explanation of the decision"
}
```
        """.trimIndent()
    }

    internal fun buildUserPrompt(
        newContent: String,
        existingContent: String,
        similarity: Float,
    ): String {
        return """
Existing memory: "$existingContent"
New memory: "$newContent"
Cosine similarity: ${"%.3f".format(similarity)}

Decide: merge, update, or keep_both?
        """.trimIndent()
    }

    internal fun parseDecision(response: String): MergeDecision {
        val jsonText = extractJsonObject(response)
        return try {
            json.decodeFromString<MergeDecision>(jsonText)
        } catch (_: Exception) {
            MergeDecision(action = "keep_both", reason = "Failed to parse LLM response")
        }
    }

    private fun extractJsonObject(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) return "{}"
        return text.substring(start, end + 1)
    }

    internal fun executeMerge(
        candidate: MemoryEntry,
        existing: MemoryEntry,
        decision: MergeDecision,
    ): SynthesisResult.Merged {
        val now = System.currentTimeMillis()

        val mergedFromIds = buildList {
            if (existing.mergedFrom.isNotBlank()) {
                addAll(existing.mergedFrom.split(","))
            }
            add(existing.id.toString())
        }.joinToString(",")

        val combinedKeywords = buildSet {
            if (existing.keywords.isNotBlank()) {
                addAll(existing.keywords.split(","))
            }
            if (candidate.keywords.isNotBlank()) {
                addAll(candidate.keywords.split(","))
            }
        }.joinToString(",")

        val merged = MemoryEntry(
            content = decision.mergedContent,
            category = candidate.category,
            source = candidate.source,
            embedding = candidate.embedding,
            keywords = combinedKeywords,
            importance = maxOf(candidate.importance, existing.importance),
            createdAt = minOf(candidate.createdAt, existing.createdAt),
            updatedAt = now,
            accessCount = existing.accessCount,
            lastAccessedAt = existing.lastAccessedAt,
            mergedFrom = mergedFromIds,
            originalTimestamp = candidate.originalTimestamp.ifBlank { existing.originalTimestamp },
        )

        database.memoryEntryBox.put(merged)
        database.memoryEntryBox.remove(existing.id)

        return SynthesisResult.Merged(merged)
    }
}
