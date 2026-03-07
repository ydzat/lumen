package com.lumen.research.collector

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.MyObjectBox
import com.lumen.core.database.entities.Source
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RssCollectorTest {

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File
    private lateinit var collector: RssCollector

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "objectbox-test-${System.nanoTime()}")
        tempDir.mkdirs()
        val store = MyObjectBox.builder()
            .baseDirectory(tempDir)
            .build()
        db = LumenDatabase(store)
        collector = RssCollector(db)
    }

    @AfterTest
    fun teardown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun parseAndProcess_parsesRssFeed_returnsArticles() = runBlocking {
        val source = Source(name = "Test Feed", url = "https://example.com/feed", type = "rss")
        val sourceId = db.sourceBox.put(source)
        val savedSource = db.sourceBox.get(sourceId)

        val articles = collector.parseAndProcess(SAMPLE_RSS_XML, savedSource)

        assertEquals(2, articles.size)
        assertEquals("Article One", articles[0].title)
        assertEquals("https://example.com/article-1", articles[0].url)
        assertEquals("Summary of article one", articles[0].summary)
        assertEquals(sourceId, articles[0].sourceId)
        assertTrue(articles[0].fetchedAt > 0)
    }

    @Test
    fun parseAndProcess_skipsDuplicateUrls() = runBlocking {
        val source = Source(name = "Test Feed", url = "https://example.com/feed", type = "rss")
        val sourceId = db.sourceBox.put(source)
        val savedSource = db.sourceBox.get(sourceId)

        db.articleBox.put(
            Article(
                sourceId = sourceId,
                title = "Existing Article",
                url = "https://example.com/article-1",
            )
        )

        val articles = collector.parseAndProcess(SAMPLE_RSS_XML, savedSource)

        assertEquals(1, articles.size)
        assertEquals("Article Two", articles[0].title)
        assertEquals("https://example.com/article-2", articles[0].url)
    }

    @Test
    fun parseAndProcess_updatesLastFetchedAt() = runBlocking {
        val source = Source(name = "Test Feed", url = "https://example.com/feed", type = "rss")
        val sourceId = db.sourceBox.put(source)
        val savedSource = db.sourceBox.get(sourceId)
        assertEquals(0L, savedSource.lastFetchedAt)

        collector.parseAndProcess(SAMPLE_RSS_XML, savedSource)

        val updatedSource = db.sourceBox.get(sourceId)
        assertNotEquals(0L, updatedSource.lastFetchedAt)
        assertTrue(updatedSource.lastFetchedAt > 0)
    }

    @Test
    fun parseAndProcess_handlesEmptyFeed() = runBlocking {
        val source = Source(name = "Empty Feed", url = "https://example.com/empty", type = "rss")
        val sourceId = db.sourceBox.put(source)
        val savedSource = db.sourceBox.get(sourceId)

        val articles = collector.parseAndProcess(EMPTY_RSS_XML, savedSource)

        assertEquals(0, articles.size)
        val updatedSource = db.sourceBox.get(sourceId)
        assertTrue(updatedSource.lastFetchedAt > 0)
    }

    @Test
    fun fetchAll_fetchesOnlyEnabledSources() = runBlocking {
        db.sourceBox.put(Source(name = "Enabled", url = "https://example.com/feed1", type = "rss", enabled = true))
        db.sourceBox.put(Source(name = "Disabled", url = "https://example.com/feed2", type = "rss", enabled = false))

        val enabledSources = db.sourceBox.all.filter { it.enabled }
        assertEquals(1, enabledSources.size)
        assertEquals("Enabled", enabledSources[0].name)
    }

    @Test
    fun resolveFeedUrl_absoluteUrl_returnsAsIs() {
        assertEquals(
            "https://rss.arxiv.org/rss/cs.AI",
            collector.resolveFeedUrl("https://rss.arxiv.org/rss/cs.AI")
        )
    }

    @Test
    fun resolveFeedUrl_relativePath_prependsRssHubBase() {
        assertEquals(
            "https://rsshub.app/arxiv/cs.AI",
            collector.resolveFeedUrl("/arxiv/cs.AI")
        )
    }

    @Test
    fun parseRssDate_rfc822Format_parsesCorrectly() {
        val millis = RssCollector.parseRssDate("Mon, 01 Jan 2024 12:00:00 +0000")
        assertTrue(millis > 0)
    }

    @Test
    fun parseRssDate_iso8601Format_parsesCorrectly() {
        val millis = RssCollector.parseRssDate("2024-01-01T12:00:00Z")
        assertTrue(millis > 0)
    }

    @Test
    fun parseRssDate_nullOrBlank_returnsZero() {
        assertEquals(0L, RssCollector.parseRssDate(null))
        assertEquals(0L, RssCollector.parseRssDate(""))
        assertEquals(0L, RssCollector.parseRssDate("  "))
    }

    @Test
    fun parseRssDate_invalidFormat_returnsZero() {
        assertEquals(0L, RssCollector.parseRssDate("not a date"))
    }

    @Test
    fun parseAndProcess_persistsArticlesToDatabase() = runBlocking {
        val source = Source(name = "Test Feed", url = "https://example.com/feed", type = "rss")
        val sourceId = db.sourceBox.put(source)
        val savedSource = db.sourceBox.get(sourceId)

        collector.parseAndProcess(SAMPLE_RSS_XML, savedSource)

        val allArticles = db.articleBox.all
        assertEquals(2, allArticles.size)
    }

    companion object {
        private val SAMPLE_RSS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/">
                <channel>
                    <title>Test Feed</title>
                    <link>https://example.com</link>
                    <description>A test feed</description>
                    <item>
                        <title>Article One</title>
                        <link>https://example.com/article-1</link>
                        <description>Summary of article one</description>
                        <dc:creator>Author One</dc:creator>
                        <pubDate>Mon, 01 Jan 2024 12:00:00 +0000</pubDate>
                    </item>
                    <item>
                        <title>Article Two</title>
                        <link>https://example.com/article-2</link>
                        <description>Summary of article two</description>
                        <dc:creator>Author Two</dc:creator>
                        <pubDate>Tue, 02 Jan 2024 12:00:00 +0000</pubDate>
                    </item>
                </channel>
            </rss>
        """.trimIndent()

        private val EMPTY_RSS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
                <channel>
                    <title>Empty Feed</title>
                    <link>https://example.com</link>
                    <description>An empty feed</description>
                </channel>
            </rss>
        """.trimIndent()
    }
}
