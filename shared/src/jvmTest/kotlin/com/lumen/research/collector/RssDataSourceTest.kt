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

class RssDataSourceTest {

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File
    private lateinit var dataSource: RssDataSource

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "objectbox-rss-test-${System.nanoTime()}")
        tempDir.mkdirs()
        val store = MyObjectBox.builder()
            .baseDirectory(tempDir)
            .build()
        db = LumenDatabase(store)
        dataSource = RssDataSource(db)
    }

    @AfterTest
    fun teardown() {
        db.close()
        tempDir.deleteRecursively()
    }

    private fun createContext(budget: Int = 100): FetchContext {
        return FetchContext(
            activeProjects = emptyList(),
            keywords = emptySet(),
            categories = emptySet(),
            remainingBudget = budget,
        )
    }

    @Test
    fun parseAndProcess_parsesRssFeed_returnsArticles() = runBlocking {
        val source = Source(name = "Test Feed", url = "https://example.com/feed", type = "RSS")
        val sourceId = db.sourceBox.put(source)
        val savedSource = db.sourceBox.get(sourceId)

        val articles = dataSource.parseAndProcess(SAMPLE_RSS_XML, savedSource)

        assertEquals(2, articles.size)
        assertEquals("Article One", articles[0].title)
        assertEquals("https://example.com/article-1", articles[0].url)
        assertEquals("Summary of article one", articles[0].summary)
        assertEquals(sourceId, articles[0].sourceId)
        assertEquals("RSS", articles[0].sourceType)
        assertTrue(articles[0].fetchedAt > 0)
    }

    @Test
    fun parseAndProcess_setsSourceType() = runBlocking {
        val source = Source(name = "Test Feed", url = "https://example.com/feed", type = "RSS")
        val sourceId = db.sourceBox.put(source)
        val savedSource = db.sourceBox.get(sourceId)

        val articles = dataSource.parseAndProcess(SAMPLE_RSS_XML, savedSource)

        assertTrue(articles.all { it.sourceType == "RSS" })
    }

    @Test
    fun parseAndProcess_handlesEmptyFeed() = runBlocking {
        val source = Source(name = "Empty Feed", url = "https://example.com/empty", type = "RSS")
        val sourceId = db.sourceBox.put(source)
        val savedSource = db.sourceBox.get(sourceId)

        val articles = dataSource.parseAndProcess(EMPTY_RSS_XML, savedSource)

        assertEquals(0, articles.size)
    }

    @Test
    fun resolveFeedUrl_absoluteUrl_returnsAsIs() {
        assertEquals(
            "https://rss.arxiv.org/rss/cs.AI",
            dataSource.resolveFeedUrl("https://rss.arxiv.org/rss/cs.AI"),
        )
    }

    @Test
    fun resolveFeedUrl_relativePath_prependsRssHubBase() {
        assertEquals(
            "https://rsshub.app/arxiv/cs.AI",
            dataSource.resolveFeedUrl("/arxiv/cs.AI"),
        )
    }

    @Test
    fun parseRssDate_rfc822Format_parsesCorrectly() {
        val millis = RssDataSource.parseRssDate("Mon, 01 Jan 2024 12:00:00 +0000")
        assertTrue(millis > 0)
    }

    @Test
    fun parseRssDate_iso8601Format_parsesCorrectly() {
        val millis = RssDataSource.parseRssDate("2024-01-01T12:00:00Z")
        assertTrue(millis > 0)
    }

    @Test
    fun parseRssDate_nullOrBlank_returnsZero() {
        assertEquals(0L, RssDataSource.parseRssDate(null))
        assertEquals(0L, RssDataSource.parseRssDate(""))
        assertEquals(0L, RssDataSource.parseRssDate("  "))
    }

    @Test
    fun parseRssDate_invalidFormat_returnsZero() {
        assertEquals(0L, RssDataSource.parseRssDate("not a date"))
    }

    @Test
    fun parseAndProcess_respectsBudgetViaTake() = runBlocking {
        val source = Source(name = "Test Feed", url = "https://example.com/feed", type = "RSS")
        val sourceId = db.sourceBox.put(source)
        val savedSource = db.sourceBox.get(sourceId)

        val articles = dataSource.parseAndProcess(SAMPLE_RSS_XML, savedSource)

        val limited = articles.take(1)
        assertEquals(1, limited.size)
        assertEquals("Article One", limited[0].title)
    }

    @Test
    fun fetch_withUnreachableSource_returnsError() = runBlocking {
        val source = Source(name = "Bad Feed", url = "https://invalid.test.example/feed", type = "RSS")
        db.sourceBox.put(source)
        val savedSource = db.sourceBox.all.first()

        val result = dataSource.fetch(listOf(savedSource), createContext())

        assertTrue(result.articles.isEmpty())
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0].contains("Bad Feed"))
        assertEquals(SourceType.RSS, result.sourceType)
    }

    @Test
    fun fetch_withEmptySources_returnsEmpty() = runBlocking {
        val result = dataSource.fetch(emptyList(), createContext())

        assertTrue(result.articles.isEmpty())
        assertTrue(result.errors.isEmpty())
        assertEquals(SourceType.RSS, result.sourceType)
    }

    @Test
    fun type_isRss() {
        assertEquals(SourceType.RSS, dataSource.type)
    }

    @Test
    fun displayName_isRss() {
        assertEquals("RSS", dataSource.displayName)
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
