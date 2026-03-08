package com.lumen.research.collector

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.MyObjectBox
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeduplicatorTest {

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "objectbox-dedup-test-${System.nanoTime()}")
        tempDir.mkdirs()
        val store = MyObjectBox.builder().baseDirectory(tempDir).build()
        db = LumenDatabase(store)
    }

    @AfterTest
    fun teardown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun deduplicate_withNoOverlap_keepsAll() {
        val dedup = Deduplicator(db)
        val articles = listOf(
            Article(title = "Article A", url = "https://example.com/a"),
            Article(title = "Article B", url = "https://example.com/b"),
        )

        val result = dedup.deduplicate(articles)

        assertEquals(2, result.unique.size)
        assertEquals(0, result.duplicatesRemoved)
    }

    @Test
    fun deduplicate_withDuplicateUrls_removesOne() {
        val dedup = Deduplicator(db)
        val articles = listOf(
            Article(title = "Article A", url = "https://example.com/same"),
            Article(title = "Article B", url = "https://example.com/same"),
        )

        val result = dedup.deduplicate(articles)

        assertEquals(1, result.unique.size)
        assertEquals(1, result.duplicatesRemoved)
    }

    @Test
    fun deduplicate_withDuplicateDoi_removesOne() {
        val dedup = Deduplicator(db)
        val articles = listOf(
            Article(title = "Paper A", url = "https://a.com/1", doi = "10.1234/test"),
            Article(title = "Paper B", url = "https://b.com/2", doi = "10.1234/test"),
        )

        val result = dedup.deduplicate(articles)

        assertEquals(1, result.unique.size)
        assertEquals(1, result.duplicatesRemoved)
    }

    @Test
    fun deduplicate_withDuplicateArxivId_removesOne() {
        val dedup = Deduplicator(db)
        val articles = listOf(
            Article(title = "Paper X", url = "https://arxiv.org/1", arxivId = "2401.12345"),
            Article(title = "Paper Y", url = "https://scholar.org/2", arxivId = "2401.12345"),
        )

        val result = dedup.deduplicate(articles)

        assertEquals(1, result.unique.size)
        assertEquals(1, result.duplicatesRemoved)
    }

    @Test
    fun deduplicate_withSameTitleAndAuthor_removesOne() {
        val dedup = Deduplicator(db)
        val articles = listOf(
            Article(title = "Attention Is All You Need", url = "https://a.com/1", author = "Vaswani"),
            Article(title = "attention is all you need", url = "https://b.com/2", author = "vaswani"),
        )

        val result = dedup.deduplicate(articles)

        assertEquals(1, result.unique.size)
        assertEquals(1, result.duplicatesRemoved)
    }

    @Test
    fun deduplicate_existingInDb_removesIncoming() {
        val existing = Article(
            title = "Existing Paper",
            url = "https://example.com/existing",
            doi = "10.5678/existing",
        )
        db.articleBox.put(existing)

        val dedup = Deduplicator(db)
        val incoming = listOf(
            Article(title = "New Paper", url = "https://example.com/new"),
            Article(title = "Duplicate", url = "https://other.com/dup", doi = "10.5678/existing"),
        )

        val result = dedup.deduplicate(incoming)

        assertEquals(1, result.unique.size)
        assertEquals("New Paper", result.unique[0].title)
        assertEquals(1, result.duplicatesRemoved)
    }

    @Test
    fun deduplicate_mergesMetadata_enrichesMissingDoi() {
        val existing = Article(
            title = "Paper Without DOI",
            url = "https://example.com/paper",
        )
        val existingId = db.articleBox.put(existing)

        val dedup = Deduplicator(db)
        val incoming = listOf(
            Article(
                title = "Paper Without DOI",
                url = "https://example.com/paper",
                doi = "10.1234/enriched",
                citationCount = 42,
            ),
        )

        val result = dedup.deduplicate(incoming)

        assertEquals(0, result.unique.size)
        assertEquals(1, result.duplicatesRemoved)

        val merged = db.articleBox.get(existingId)
        assertEquals("10.1234/enriched", merged.doi)
        assertEquals(42, merged.citationCount)
    }

    @Test
    fun normalizeTitle_removesSpecialCharsAndNormalizes() {
        assertEquals("attention is all you need", Deduplicator.normalizeTitle("Attention Is All You Need!"))
        assertEquals("a b c", Deduplicator.normalizeTitle("  A   B   C  "))
        assertEquals("test123", Deduplicator.normalizeTitle("test-123"))
    }

    @Test
    fun deduplicate_doiPriorityOverUrl() {
        val dedup = Deduplicator(db)
        val articles = listOf(
            Article(title = "Paper A", url = "https://different-url.com/a", doi = "10.1234/same"),
            Article(title = "Paper B", url = "https://different-url.com/b", doi = "10.1234/same"),
        )

        val result = dedup.deduplicate(articles)

        assertEquals(1, result.unique.size)
        assertTrue(result.unique[0].title == "Paper A")
    }
}
