package com.lumen.research.archiver

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.MyObjectBox
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArticleArchiverTest {

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "objectbox-archiver-test-${System.nanoTime()}")
        tempDir.mkdirs()
        val store = MyObjectBox.builder().baseDirectory(tempDir).build()
        db = LumenDatabase(store)
    }

    @AfterTest
    fun teardown() {
        db.close()
        tempDir.deleteRecursively()
    }

    private fun nowMillis(): Long = System.currentTimeMillis()
    private fun daysAgoMillis(days: Int): Long = nowMillis() - days * 86_400_000L

    // --- archiveStale ---

    @Test
    fun archiveStale_archivesOldLowRelevanceArticles() {
        db.articleBox.put(listOf(
            Article(title = "Old low", fetchedAt = daysAgoMillis(60), aiRelevanceScore = 0.1f, content = "full text"),
            Article(title = "Old low 2", fetchedAt = daysAgoMillis(45), aiRelevanceScore = 0.2f, content = "full text"),
        ))
        val archiver = ArticleArchiver(db)

        val result = archiver.archiveStale(maxAgeDays = 30, minRelevanceScore = 0.3f)

        assertEquals(2, result.archived)
        val articles = db.articleBox.all
        assertTrue(articles.all { it.archived })
    }

    @Test
    fun archiveStale_skipsStarredArticles() {
        db.articleBox.put(
            Article(title = "Starred old", fetchedAt = daysAgoMillis(60), aiRelevanceScore = 0.1f, starred = true, content = "text"),
        )
        val archiver = ArticleArchiver(db)

        val result = archiver.archiveStale(maxAgeDays = 30, minRelevanceScore = 0.3f)

        assertEquals(0, result.archived)
        assertFalse(db.articleBox.all.first().archived)
    }

    @Test
    fun archiveStale_skipsReadArticles() {
        db.articleBox.put(
            Article(title = "Read old", fetchedAt = daysAgoMillis(60), aiRelevanceScore = 0.1f, readAt = nowMillis(), content = "text"),
        )
        val archiver = ArticleArchiver(db)

        val result = archiver.archiveStale(maxAgeDays = 30, minRelevanceScore = 0.3f)

        assertEquals(0, result.archived)
        assertFalse(db.articleBox.all.first().archived)
    }

    @Test
    fun archiveStale_skipsRecentArticles() {
        db.articleBox.put(
            Article(title = "Recent low", fetchedAt = daysAgoMillis(5), aiRelevanceScore = 0.1f, content = "text"),
        )
        val archiver = ArticleArchiver(db)

        val result = archiver.archiveStale(maxAgeDays = 30, minRelevanceScore = 0.3f)

        assertEquals(0, result.archived)
        assertFalse(db.articleBox.all.first().archived)
    }

    @Test
    fun archiveStale_skipsHighRelevanceArticles() {
        db.articleBox.put(
            Article(title = "Old high", fetchedAt = daysAgoMillis(60), aiRelevanceScore = 0.8f, content = "text"),
        )
        val archiver = ArticleArchiver(db)

        val result = archiver.archiveStale(maxAgeDays = 30, minRelevanceScore = 0.3f)

        assertEquals(0, result.archived)
        assertFalse(db.articleBox.all.first().archived)
    }

    @Test
    fun archiveStale_clearsContentAndTranslation() {
        db.articleBox.put(
            Article(
                title = "Test article",
                fetchedAt = daysAgoMillis(60),
                aiRelevanceScore = 0.1f,
                content = "This is the full article content",
                aiTranslation = "This is the translation",
                aiSummary = "This is the summary",
                url = "https://example.com/article",
                embedding = floatArrayOf(0.1f, 0.2f),
            ),
        )
        val archiver = ArticleArchiver(db)

        archiver.archiveStale(maxAgeDays = 30, minRelevanceScore = 0.3f)

        val article = db.articleBox.all.first()
        assertTrue(article.archived)
        assertEquals("", article.content)
        assertEquals("", article.aiTranslation)
        assertEquals("This is the summary", article.aiSummary)
        assertEquals("Test article", article.title)
        assertEquals("https://example.com/article", article.url)
        assertTrue(article.embedding.isNotEmpty())
    }

    // --- emergencyArchive ---

    @Test
    fun emergencyArchive_archivesLowestRelevanceFirst() {
        db.articleBox.put(listOf(
            Article(title = "Low", aiRelevanceScore = 0.1f, content = "text"),
            Article(title = "Medium", aiRelevanceScore = 0.5f, content = "text"),
            Article(title = "High", aiRelevanceScore = 0.9f, content = "text"),
        ))
        val archiver = ArticleArchiver(db)

        val result = archiver.emergencyArchive(targetArticleCount = 2)

        assertEquals(1, result.archived)
        val remaining = db.articleBox.all.filter { !it.archived }
        assertEquals(2, remaining.size)
        assertTrue(remaining.all { it.aiRelevanceScore >= 0.5f })
    }

    @Test
    fun emergencyArchive_respectsTargetCount() {
        db.articleBox.put(listOf(
            Article(title = "A1", aiRelevanceScore = 0.1f, content = "text"),
            Article(title = "A2", aiRelevanceScore = 0.2f, content = "text"),
            Article(title = "A3", aiRelevanceScore = 0.3f, content = "text"),
            Article(title = "A4", aiRelevanceScore = 0.4f, content = "text"),
            Article(title = "A5", aiRelevanceScore = 0.5f, content = "text"),
        ))
        val archiver = ArticleArchiver(db)

        val result = archiver.emergencyArchive(targetArticleCount = 3)

        assertEquals(2, result.archived)
        val active = db.articleBox.all.filter { !it.archived }
        assertEquals(3, active.size)
    }

    @Test
    fun emergencyArchive_skipsStarredArticles() {
        db.articleBox.put(listOf(
            Article(title = "Starred low", aiRelevanceScore = 0.1f, starred = true, content = "text"),
            Article(title = "Unstarred low", aiRelevanceScore = 0.2f, content = "text"),
            Article(title = "Unstarred high", aiRelevanceScore = 0.9f, content = "text"),
        ))
        val archiver = ArticleArchiver(db)

        val result = archiver.emergencyArchive(targetArticleCount = 1)

        assertEquals(2, result.archived)
        val remaining = db.articleBox.all.filter { !it.archived }
        assertEquals(1, remaining.size)
        assertEquals("Starred low", remaining.first().title)
    }

    @Test
    fun emergencyArchive_noOpWhenBelowTarget() {
        db.articleBox.put(listOf(
            Article(title = "A1", content = "text"),
            Article(title = "A2", content = "text"),
        ))
        val archiver = ArticleArchiver(db)

        val result = archiver.emergencyArchive(targetArticleCount = 5)

        assertEquals(0, result.archived)
        assertTrue(db.articleBox.all.none { it.archived })
    }

    // --- restore ---

    @Test
    fun restore_unarchivesArticle() = runBlocking {
        db.articleBox.put(Article(title = "Archived", archived = true, content = "", url = ""))
        val articleId = db.articleBox.all.first().id
        val archiver = ArticleArchiver(db)

        val restored = archiver.restore(articleId)

        assertNotNull(restored)
        assertFalse(restored.archived)
        val fromDb = db.articleBox.get(articleId)
        assertFalse(fromDb.archived)
    }

    @Test
    fun restore_returnsNullForNonExistent() = runBlocking {
        val archiver = ArticleArchiver(db)

        val result = archiver.restore(99999L)

        assertNull(result)
    }

    @Test
    fun restore_returnsNullForNonArchivedArticle() = runBlocking {
        db.articleBox.put(Article(title = "Active", archived = false))
        val articleId = db.articleBox.all.first().id
        val archiver = ArticleArchiver(db)

        val result = archiver.restore(articleId)

        assertNull(result)
    }

    // --- needsEmergencyArchive ---

    @Test
    fun needsEmergencyArchive_returnsTrueWhenOverLimit() {
        db.articleBox.put(listOf(
            Article(title = "A1"),
            Article(title = "A2"),
            Article(title = "A3"),
        ))
        val archiver = ArticleArchiver(db)

        assertTrue(archiver.needsEmergencyArchive(maxArticleCount = 2))
    }

    @Test
    fun needsEmergencyArchive_returnsFalseWhenUnderLimit() {
        db.articleBox.put(listOf(
            Article(title = "A1"),
            Article(title = "A2"),
        ))
        val archiver = ArticleArchiver(db)

        assertFalse(archiver.needsEmergencyArchive(maxArticleCount = 5))
    }

    @Test
    fun needsEmergencyArchive_excludesArchivedFromCount() {
        db.articleBox.put(listOf(
            Article(title = "Active1"),
            Article(title = "Active2"),
            Article(title = "Archived1", archived = true),
        ))
        val archiver = ArticleArchiver(db)

        assertFalse(archiver.needsEmergencyArchive(maxArticleCount = 2))
    }

    // --- getArchiveStats ---

    @Test
    fun getArchiveStats_returnsCorrectCounts() {
        db.articleBox.put(listOf(
            Article(title = "Active1"),
            Article(title = "Active2"),
            Article(title = "Archived1", archived = true),
        ))
        val archiver = ArticleArchiver(db)

        val stats = archiver.getArchiveStats()

        assertEquals(3, stats.totalArticles)
        assertEquals(1, stats.archivedArticles)
        assertEquals(2, stats.activeArticles)
    }
}
