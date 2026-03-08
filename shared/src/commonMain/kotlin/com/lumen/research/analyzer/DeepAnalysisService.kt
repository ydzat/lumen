package com.lumen.research.analyzer

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.ArticleSection
import com.lumen.core.database.entities.ArticleSection_
import com.lumen.core.util.extractJsonObject
import com.lumen.core.memory.LlmCall
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class DeepAnalysisService(
    private val db: LumenDatabase,
    private val llmCall: LlmCall,
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun extractAndAnalyze(articleId: Long): List<ArticleSection> {
        val article = db.articleBox.get(articleId) ?: return emptyList()
        val content = article.content.ifBlank { article.summary }
        if (content.isBlank()) return emptyList()

        // Remove existing sections if re-analyzing
        val existing = getSections(articleId)
        if (existing.isNotEmpty()) {
            db.articleSectionBox.remove(existing)
        }

        // Phase A: Extract structure and identify key sections
        val sections = extractStructure(articleId, article.title, content)
        if (sections.isEmpty()) return emptyList()

        // Phase B: Analyze key sections
        for (section in sections) {
            if (section.isKeySection) {
                analyzeSection(section, article.title, article.keywords)
            }
        }

        // Update article deep analysis status
        val updated = article.copy(deepAnalysisStatus = DEEP_ANALYZED)
        db.articleBox.put(updated)

        return getSections(articleId)
    }

    suspend fun translateSection(sectionId: Long, targetLanguage: String): ArticleSection? {
        val section = db.articleSectionBox.get(sectionId) ?: return null

        val response = try {
            llmCall.execute(
                buildTranslationSystemPrompt(targetLanguage),
                "Section: ${section.heading}\n\n${section.content}",
            )
        } catch (_: Exception) {
            return null
        }

        val translation = parseTranslationResponse(response)
        if (translation.isNotBlank()) {
            val updated = section.copy(aiTranslation = translation)
            db.articleSectionBox.put(updated)
            return db.articleSectionBox.get(sectionId)
        }
        return null
    }

    fun getSections(articleId: Long): List<ArticleSection> {
        return db.articleSectionBox.query()
            .equal(ArticleSection_.articleId, articleId)
            .build()
            .use { it.find() }
            .sortedBy { it.sectionIndex }
    }

    private suspend fun extractStructure(
        articleId: Long,
        title: String,
        content: String,
    ): List<ArticleSection> {
        // Pre-split content locally by headings/paragraphs
        val localSections = splitIntoSections(content)

        // Build skeleton for LLM (headings + first 200 chars per section)
        val skeleton = localSections.mapIndexed { i, s ->
            val preview = s.content.take(200)
            "[$i] ${s.heading} (level ${s.level}): $preview..."
        }.joinToString("\n\n")

        val structureResponse = try {
            llmCall.execute(
                STRUCTURE_SYSTEM_PROMPT,
                "Title: $title\n\nArticle structure:\n$skeleton",
            )
        } catch (_: Exception) {
            null
        }

        val keySectionIndices = if (structureResponse != null) {
            parseStructureResponse(structureResponse, localSections.size)
        } else {
            // Fallback: mark all sections as key
            localSections.indices.toSet()
        }

        // Persist sections
        val entities = localSections.mapIndexed { index, s ->
            ArticleSection(
                articleId = articleId,
                sectionIndex = index,
                heading = s.heading,
                content = s.content,
                level = s.level,
                isKeySection = index in keySectionIndices,
            )
        }
        db.articleSectionBox.put(entities)
        return entities
    }

    private suspend fun analyzeSection(
        section: ArticleSection,
        articleTitle: String,
        articleKeywords: String,
    ) {
        val systemPrompt = buildSectionAnalysisSystemPrompt(articleTitle, articleKeywords)
        val response = try {
            llmCall.execute(
                systemPrompt,
                "Section: ${section.heading}\n\n${section.content}",
            )
        } catch (_: Exception) {
            return
        }

        val commentary = parseSectionAnalysisResponse(response)
        if (commentary.isNotBlank()) {
            val updated = section.copy(aiCommentary = commentary)
            db.articleSectionBox.put(updated)
        }
    }

    // --- Content splitting ---

    internal fun splitIntoSections(content: String): List<LocalSection> {
        val sections = mutableListOf<LocalSection>()

        // Try heading-based splitting first
        val headingPattern = Regex(
            """(?:^|\n)(#{1,3})\s+(.+)|<h([1-3])[^>]*>(.*?)</h\3>""",
            RegexOption.IGNORE_CASE,
        )
        val matches = headingPattern.findAll(content).toList()

        if (matches.isNotEmpty()) {
            // Split by headings
            for (i in matches.indices) {
                val match = matches[i]
                val level = match.groupValues[1].length.takeIf { it > 0 }
                    ?: match.groupValues[3].toIntOrNull() ?: 1
                val heading = match.groupValues[2].ifBlank { match.groupValues[4] }.trim()
                val start = match.range.last + 1
                val end = if (i + 1 < matches.size) matches[i + 1].range.first else content.length
                val sectionContent = content.substring(start, end).trim()
                if (sectionContent.isNotBlank()) {
                    sections.add(LocalSection(heading, sectionContent, level))
                }
            }

            // Add content before first heading as introduction
            val beforeFirst = content.substring(0, matches[0].range.first).trim()
            if (beforeFirst.isNotBlank()) {
                sections.add(0, LocalSection("Introduction", beforeFirst, 1))
            }
        }

        if (sections.isEmpty()) {
            // Fall back to paragraph-based splitting
            val paragraphs = content.split(Regex("\n{2,}")).filter { it.isNotBlank() }
            if (paragraphs.size <= 3) {
                sections.add(LocalSection("Full Content", content.trim(), 1))
            } else {
                paragraphs.forEachIndexed { index, para ->
                    val heading = when (index) {
                        0 -> "Opening"
                        paragraphs.lastIndex -> "Conclusion"
                        else -> "Section ${index + 1}"
                    }
                    sections.add(LocalSection(heading, para.trim(), 2))
                }
            }
        }

        return sections
    }

    // --- Response parsing ---

    internal fun parseStructureResponse(response: String, sectionCount: Int): Set<Int> {
        val jsonText = extractJsonObject(response)
        return try {
            val result = json.decodeFromString<StructureResult>(jsonText)
            val valid = result.keySections
                .filter { it in 0 until sectionCount }
                .toSet()
            valid.ifEmpty { (0 until sectionCount).toSet() }
        } catch (_: Exception) {
            // Fallback: all sections are key
            (0 until sectionCount).toSet()
        }
    }

    internal fun parseSectionAnalysisResponse(response: String): String {
        val jsonText = extractJsonObject(response)
        return try {
            val result = json.decodeFromString<CommentaryResult>(jsonText)
            result.commentary
        } catch (_: Exception) {
            ""
        }
    }

    internal fun parseTranslationResponse(response: String): String {
        val jsonText = extractJsonObject(response)
        return try {
            val result = json.decodeFromString<TranslationResult>(jsonText)
            result.translation
        } catch (_: Exception) {
            ""
        }
    }

    // --- Data classes ---

    internal data class LocalSection(
        val heading: String,
        val content: String,
        val level: Int,
    )

    @Serializable
    internal data class StructureResult(
        val keySections: List<Int> = emptyList(),
    )

    @Serializable
    internal data class CommentaryResult(
        val commentary: String = "",
    )

    @Serializable
    internal data class TranslationResult(
        val translation: String = "",
    )

    companion object {
        const val DEEP_ANALYZED = "deep_analyzed"

        internal const val STRUCTURE_SYSTEM_PROMPT = """You are an expert at analyzing article structure. Given the outline of an article with section previews, identify which sections contain the most important content.

## Rules

1. Review each section's heading, level, and content preview.
2. Select the sections that contain: novel contributions, main results, methodology innovations, or critical conclusions.
3. Exclude sections like acknowledgments, references, author bios, or boilerplate.
4. Aim to mark 30-50% of sections as key.
5. Return section indices (0-based) of the key sections.

## Output Format

Return a JSON object only, with no other text:

{"keySections": [0, 2, 5]}"""

        internal fun buildSectionAnalysisSystemPrompt(
            articleTitle: String,
            articleKeywords: String,
        ): String = """You are a research article analyst providing expert commentary on a specific section.

## Context
Article title: $articleTitle
Article keywords: $articleKeywords

## Rules

1. Write 2-4 sentences of expert commentary on this section.
2. Highlight: key findings, methodological strengths/weaknesses, connections to broader field, or practical implications.
3. Be specific and technical. Do not just summarize — add analytical value.

## Output Format

Return a JSON object only, with no other text:

{"commentary": "Your expert commentary here."}"""

        internal fun buildTranslationSystemPrompt(targetLanguage: String): String =
            """You are a professional translator specializing in academic and technical texts.

## Rules

1. Translate to $targetLanguage.
2. Preserve technical terms, formulas, and proper nouns.
3. Maintain the original paragraph structure.
4. For ambiguous terms, keep the original in parentheses after the translation.

## Output Format

Return a JSON object only, with no other text:

{"translation": "The translated text here."}"""
    }
}
