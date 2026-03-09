package com.lumen.core.memory

import com.lumen.core.util.formatEpochDate
import kotlinx.serialization.json.Json

class SemanticCompressor(private val llmCall: LlmCall) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun compress(conversation: String, currentTime: Long): List<ExtractedMemory> {
        val systemPrompt = buildSystemPrompt(currentTime)
        val response = llmCall.execute(systemPrompt, conversation)
        return parseResponse(response)
    }

    internal fun buildSystemPrompt(currentTime: Long): String {
        val currentDate = formatEpochDate(currentTime)
        return """
You are a memory extraction assistant. Your task is to extract atomic, self-contained facts from a conversation.

## Rules

1. **Extract atomic facts**: Each extracted memory must be a single, independent fact. Do not combine multiple facts into one entry.
2. **Resolve coreferences**: Replace all pronouns and ambiguous references with the specific entity they refer to. For example, "he" should become "the user" or the person's actual name.
3. **Absolutize timestamps**: Convert all relative time references to absolute dates using today's date ($currentDate) as the anchor. For example, "yesterday" becomes the actual date, "next week" becomes the actual date range.
4. **Classify each fact**: Assign a category (preference, fact, event, opinion, plan, habit) and relevant keywords.
5. **Rate importance**: Assign an importance score from 0.0 (trivial) to 1.0 (critical) based on how useful this fact would be for future conversations.
6. **Skip filler**: Do not extract greetings, acknowledgments, or social filler that carry no factual information.

## Output Format

Return a JSON array only, with no other text. Each element must have these fields:

```json
[
  {
    "content": "Self-contained factual statement",
    "category": "preference|fact|event|opinion|plan|habit",
    "keywords": ["keyword1", "keyword2"],
    "importance": 0.8,
    "originalTimestamp": "2026-03-07 or empty if no time reference"
  }
]
```

If no meaningful facts can be extracted, return an empty array: []
        """.trimIndent()
    }

    internal fun parseResponse(response: String): List<ExtractedMemory> {
        val jsonText = extractJsonArray(response)
        return try {
            json.decodeFromString<List<ExtractedMemory>>(jsonText)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractJsonArray(text: String): String {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start == -1 || end == -1 || end <= start) return "[]"
        return text.substring(start, end + 1)
    }

}
