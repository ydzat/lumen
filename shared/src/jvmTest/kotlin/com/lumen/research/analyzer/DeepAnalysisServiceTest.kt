package com.lumen.research.analyzer

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.MyObjectBox
import com.lumen.core.memory.LlmCall
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeepAnalysisServiceTest {

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File
    private var llmCallCount = 0

    private val structureResponse = """{"keySections": [0, 2]}"""
    private val commentaryResponse = """{"commentary": "This section presents a novel approach."}"""
    private val translationResponse = """{"translation": "This is the translated text."}"""

    private val fakeLlmCall = LlmCall { system, _ ->
        llmCallCount++
        when {
            "article structure" in system -> structureResponse
            "expert commentary" in system -> commentaryResponse
            "professional translator" in system -> translationResponse
            else -> "{}"
        }
    }

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "objectbox-test-${System.nanoTime()}")
        tempDir.mkdirs()
        val store = MyObjectBox.builder()
            .baseDirectory(tempDir)
            .build()
        db = LumenDatabase(store)
        llmCallCount = 0
    }

    @AfterTest
    fun teardown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun extractAndAnalyze_withHeadedContent_createsSections() = runBlocking {
        val articleId = db.articleBox.put(
            Article(
                title = "Test Paper",
                url = "https://example.com/paper",
                content = """## Introduction
This is the introduction section with some content.

## Methods
This section describes the methods used.

## Results
Here are the results of the experiment.
""",
            ),
        )

        val service = DeepAnalysisService(db, fakeLlmCall)
        val sections = service.extractAndAnalyze(articleId)

        assertTrue(sections.isNotEmpty())
        assertTrue(sections.any { it.heading == "Introduction" })
        assertTrue(sections.any { it.heading == "Methods" })
        assertTrue(sections.any { it.heading == "Results" })
    }

    @Test
    fun extractAndAnalyze_marksKeySections() = runBlocking {
        val articleId = db.articleBox.put(
            Article(
                title = "Test Paper",
                url = "https://example.com/paper",
                content = """## Introduction
Introduction content here.

## Methods
Methods content here.

## Results
Results content here.
""",
            ),
        )

        val service = DeepAnalysisService(db, fakeLlmCall)
        val sections = service.extractAndAnalyze(articleId)

        val keySections = sections.filter { it.isKeySection }
        assertTrue(keySections.isNotEmpty())
    }

    @Test
    fun extractAndAnalyze_analyzesKeySections() = runBlocking {
        val articleId = db.articleBox.put(
            Article(
                title = "Test Paper",
                url = "https://example.com/paper",
                content = """## Introduction
Introduction content here.

## Methods
Methods content here.

## Results
Results content here.
""",
            ),
        )

        val service = DeepAnalysisService(db, fakeLlmCall)
        val sections = service.extractAndAnalyze(articleId)

        val analyzedSections = sections.filter { it.aiCommentary.isNotBlank() }
        assertTrue(analyzedSections.isNotEmpty())
        assertTrue(analyzedSections.all { it.isKeySection })
    }

    @Test
    fun extractAndAnalyze_updatesArticleDeepAnalysisStatus() = runBlocking {
        val articleId = db.articleBox.put(
            Article(
                title = "Test Paper",
                url = "https://example.com/paper",
                content = "## Intro\nSome content here.",
            ),
        )

        val service = DeepAnalysisService(db, fakeLlmCall)
        service.extractAndAnalyze(articleId)

        val updated = db.articleBox.get(articleId)
        assertEquals(DeepAnalysisService.DEEP_ANALYZED, updated.deepAnalysisStatus)
    }

    @Test
    fun extractAndAnalyze_withPlainText_createsSingleSection() = runBlocking {
        val articleId = db.articleBox.put(
            Article(
                title = "Short Article",
                url = "https://example.com/short",
                content = "This is a short article with no headings.",
            ),
        )

        val service = DeepAnalysisService(db, fakeLlmCall)
        val sections = service.extractAndAnalyze(articleId)

        assertEquals(1, sections.size)
        assertEquals("Full Content", sections[0].heading)
    }

    @Test
    fun extractAndAnalyze_withEmptyContent_returnsEmpty() = runBlocking {
        val articleId = db.articleBox.put(
            Article(
                title = "Empty Article",
                url = "https://example.com/empty",
                content = "",
                summary = "",
            ),
        )

        val service = DeepAnalysisService(db, fakeLlmCall)
        val sections = service.extractAndAnalyze(articleId)

        assertTrue(sections.isEmpty())
    }

    @Test
    fun translateSection_producesTranslation() = runBlocking {
        val articleId = db.articleBox.put(
            Article(
                title = "Test",
                url = "https://example.com/t",
                content = "## Intro\nContent here.",
            ),
        )

        val service = DeepAnalysisService(db, fakeLlmCall)
        service.extractAndAnalyze(articleId)
        val sections = service.getSections(articleId)
        assertTrue(sections.isNotEmpty())

        val translated = service.translateSection(sections[0].id, "zh")
        assertNotNull(translated)
        assertEquals("This is the translated text.", translated.aiTranslation)
    }

    @Test
    fun getSections_returnsOrderedByIndex() = runBlocking {
        val articleId = db.articleBox.put(
            Article(
                title = "Multi Section",
                url = "https://example.com/multi",
                content = """## First
Content 1.

## Second
Content 2.

## Third
Content 3.
""",
            ),
        )

        val service = DeepAnalysisService(db, fakeLlmCall)
        service.extractAndAnalyze(articleId)
        val sections = service.getSections(articleId)

        for (i in 1 until sections.size) {
            assertTrue(sections[i].sectionIndex >= sections[i - 1].sectionIndex)
        }
    }

    // --- analyzeSingleSection tests ---

    @Test
    fun analyzeSingleSection_createsAndAnalyzes() = runBlocking {
        val articleId = db.articleBox.put(
            Article(
                title = "Test Paper",
                url = "https://example.com/paper",
                content = """## Introduction
Introduction content here.

## Methods
Methods content here.

## Results
Results content here.
""",
            ),
        )

        val service = DeepAnalysisService(db, fakeLlmCall)
        val result = service.analyzeSingleSection(articleId, 0)

        assertNotNull(result)
        assertEquals(0, result.sectionIndex)
        assertTrue(result.aiCommentary.isNotBlank())

        // Verify sections were created in DB
        val allSections = service.getSections(articleId)
        assertTrue(allSections.isNotEmpty())
    }

    @Test
    fun analyzeSingleSection_skipsAlreadyAnalyzed() = runBlocking {
        val articleId = db.articleBox.put(
            Article(
                title = "Test Paper",
                url = "https://example.com/paper",
                content = """## Introduction
Introduction content here.

## Methods
Methods content here.
""",
            ),
        )

        val service = DeepAnalysisService(db, fakeLlmCall)
        service.analyzeSingleSection(articleId, 0)
        val callsAfterFirst = llmCallCount

        // Second call should not invoke LLM again
        service.analyzeSingleSection(articleId, 0)
        // Only structure call + analysis call from the first invocation
        // Second invocation should not add any LLM calls
        assertEquals(callsAfterFirst, llmCallCount)
    }

    // --- Parsing unit tests ---

    @Test
    fun parseStructureResponse_validJson_returnsIndices() {
        val service = DeepAnalysisService(db, fakeLlmCall)
        val result = service.parseStructureResponse("""{"keySections": [0, 2, 4]}""", 5)

        assertEquals(setOf(0, 2, 4), result)
    }

    @Test
    fun parseStructureResponse_outOfBoundsIndices_filtered() {
        val service = DeepAnalysisService(db, fakeLlmCall)
        val result = service.parseStructureResponse("""{"keySections": [0, 2, 10]}""", 5)

        assertEquals(setOf(0, 2), result)
    }

    @Test
    fun parseStructureResponse_malformedJson_returnsFallback() {
        val service = DeepAnalysisService(db, fakeLlmCall)
        val result = service.parseStructureResponse("not json", 3)

        assertEquals(setOf(0, 1, 2), result)
    }

    @Test
    fun parseSectionAnalysisResponse_validJson() {
        val service = DeepAnalysisService(db, fakeLlmCall)
        val result = service.parseSectionAnalysisResponse(
            """{"commentary": "Great work on this section."}""",
        )

        assertEquals("Great work on this section.", result)
    }

    @Test
    fun parseSectionAnalysisResponse_invalidJson_returnsEmpty() {
        val service = DeepAnalysisService(db, fakeLlmCall)
        val result = service.parseSectionAnalysisResponse("invalid")

        assertEquals("", result)
    }

    @Test
    fun parseTranslationResponse_validJson() {
        val service = DeepAnalysisService(db, fakeLlmCall)
        val result = service.parseTranslationResponse(
            """{"translation": "Translated text here."}""",
        )

        assertEquals("Translated text here.", result)
    }

    @Test
    fun splitIntoSections_markdownHeadings() {
        val service = DeepAnalysisService(db, fakeLlmCall)
        val sections = service.splitIntoSections(
            """## Introduction
Intro text here.

## Methods
Methods text here.

### Sub Method
Sub method text.
""",
        )

        assertTrue(sections.size >= 3)
        assertTrue(sections.any { it.heading == "Introduction" && it.level == 2 })
        assertTrue(sections.any { it.heading == "Methods" && it.level == 2 })
        assertTrue(sections.any { it.heading == "Sub Method" && it.level == 3 })
    }

    @Test
    fun splitIntoSections_plainTextParagraphs() {
        val service = DeepAnalysisService(db, fakeLlmCall)
        val sections = service.splitIntoSections(
            """First paragraph of text.

Second paragraph of text.

Third paragraph of text.

Fourth paragraph of text.
""",
        )

        assertTrue(sections.size >= 3)
        assertEquals("Opening", sections[0].heading)
    }

    @Test
    fun splitIntoSections_singleParagraph() {
        val service = DeepAnalysisService(db, fakeLlmCall)
        val sections = service.splitIntoSections("A single short paragraph.")

        assertEquals(1, sections.size)
        assertEquals("Full Content", sections[0].heading)
    }
}
