package com.lumen.research.collector

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.MyObjectBox
import com.lumen.core.database.entities.Source
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArxivApiDataSourceTest {

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "objectbox-arxiv-test-${System.nanoTime()}")
        tempDir.mkdirs()
        val store = MyObjectBox.builder().baseDirectory(tempDir).build()
        db = LumenDatabase(store)
    }

    @AfterTest
    fun teardown() {
        db.close()
        tempDir.deleteRecursively()
    }

    private fun createDataSource(responseXml: String, statusCode: HttpStatusCode = HttpStatusCode.OK): ArxivApiDataSource {
        val mockEngine = MockEngine {
            respond(
                content = responseXml,
                status = statusCode,
                headers = headersOf(HttpHeaders.ContentType, "application/atom+xml"),
            )
        }
        return ArxivApiDataSource(db, HttpClient(mockEngine))
    }

    private fun createContext(
        keywords: Set<String> = emptySet(),
        budget: Int = 100,
    ): FetchContext {
        return FetchContext(
            activeProjects = emptyList(),
            keywords = keywords,
            categories = emptySet(),
            remainingBudget = budget,
        )
    }

    @Test
    fun parseAtomResponse_extractsAllFields() {
        val ds = createDataSource(SAMPLE_RESPONSE)
        val articles = ds.parseAtomResponse(SAMPLE_RESPONSE)

        assertEquals(1, articles.size)
        val article = articles[0]
        assertEquals("Attention Is All You Need", article.title)
        assertEquals("We propose a new simple network architecture", article.summary)
        assertEquals("Ashish Vaswani, Noam Shazeer", article.author)
        assertEquals("1706.03762", article.arxivId)
        assertEquals("10.1234/test.doi", article.doi)
        assertEquals("https://arxiv.org/abs/1706.03762v7", article.url)
        assertEquals("ARXIV_API", article.sourceType)
        assertTrue(article.publishedAt > 0)
    }

    @Test
    fun parseAtomResponse_withMultipleEntries_parsesAll() {
        val ds = createDataSource(MULTI_ENTRY_RESPONSE)
        val articles = ds.parseAtomResponse(MULTI_ENTRY_RESPONSE)

        assertEquals(2, articles.size)
        assertEquals("Paper One", articles[0].title)
        assertEquals("Paper Two", articles[1].title)
    }

    @Test
    fun parseAtomResponse_withEmptyFeed_returnsEmpty() {
        val ds = createDataSource(EMPTY_RESPONSE)
        val articles = ds.parseAtomResponse(EMPTY_RESPONSE)

        assertTrue(articles.isEmpty())
    }

    @Test
    fun extractArxivId_fromStandardUrl() {
        assertEquals("1706.03762", ArxivApiDataSource.extractArxivId("http://arxiv.org/abs/1706.03762v7"))
        assertEquals("2401.12345", ArxivApiDataSource.extractArxivId("http://arxiv.org/abs/2401.12345v1"))
        assertEquals("2401.12345", ArxivApiDataSource.extractArxivId("http://arxiv.org/abs/2401.12345"))
    }

    @Test
    fun parseIso8601_validDate() {
        val millis = ArxivApiDataSource.parseIso8601("2017-06-12T17:57:34Z")
        assertTrue(millis > 0)
    }

    @Test
    fun parseIso8601_invalidDate_returnsZero() {
        assertEquals(0L, ArxivApiDataSource.parseIso8601(null))
        assertEquals(0L, ArxivApiDataSource.parseIso8601(""))
        assertEquals(0L, ArxivApiDataSource.parseIso8601("not-a-date"))
    }

    @Test
    fun parseConfig_validJson() {
        val config = ArxivApiDataSource.parseConfig("""{"categories":["cs.AI","cs.LG"],"maxResults":25}""")
        assertEquals(listOf("cs.AI", "cs.LG"), config?.categories)
        assertEquals(25, config?.maxResults)
    }

    @Test
    fun parseConfig_emptyString_returnsNull() {
        val config = ArxivApiDataSource.parseConfig("")
        assertEquals(null, config)
    }

    @Test
    fun parseConfig_invalidJson_returnsNull() {
        val config = ArxivApiDataSource.parseConfig("not json")
        assertEquals(null, config)
    }

    @Test
    fun buildQueryUrl_withCategoriesAndKeywords() {
        val ds = createDataSource("")
        val source = Source(name = "arXiv", url = "", type = "ARXIV_API", config = """{"categories":["cs.AI","cs.LG"]}""")
        val context = createContext(keywords = setOf("transformer", "attention"))

        val url = ds.buildQueryUrl(source, context, 50)

        assertTrue(url.contains("cat:cs.AI"))
        assertTrue(url.contains("cat:cs.LG"))
        assertTrue(url.contains("all:transformer"))
        assertTrue(url.contains("all:attention"))
        assertTrue(url.contains("max_results=50"))
        assertTrue(url.startsWith(ArxivApiDataSource.BASE_URL))
    }

    @Test
    fun buildQueryUrl_withNoCategoriesOrKeywords_fallsBackToDefault() {
        val ds = createDataSource("")
        val source = Source(name = "arXiv", url = "", type = "ARXIV_API")
        val context = createContext()

        val url = ds.buildQueryUrl(source, context, 50)

        assertTrue(url.contains("cat:cs.AI"))
    }

    @Test
    fun buildQueryUrl_withSourceUrl_extractsCategory() {
        val ds = createDataSource("")
        val source = Source(name = "arXiv", url = "https://export.arxiv.org/api/query?search_query=cat:cs.CV", type = "ARXIV_API")
        val context = createContext()

        val url = ds.buildQueryUrl(source, context, 50)

        assertTrue(url.contains("cat:cs.CV"), "URL should contain extracted category cs.CV")
        assertTrue(url.contains("sortBy=submittedDate"), "URL should have sort parameters")
        assertTrue(url.contains("max_results=50"), "URL should respect budget limit")
    }

    @Test
    fun buildQueryUrl_respectsBudgetLimit() {
        val ds = createDataSource("")
        val source = Source(name = "arXiv", url = "", type = "ARXIV_API", config = """{"categories":["cs.AI"],"maxResults":100}""")
        val context = createContext()

        val url = ds.buildQueryUrl(source, context, 10)

        assertTrue(url.contains("max_results=10"))
    }

    @Test
    fun fetch_withValidResponse_returnsArticles() = kotlinx.coroutines.runBlocking {
        val ds = createDataSource(SAMPLE_RESPONSE)
        val source = Source(name = "arXiv", url = "", type = "ARXIV_API", config = """{"categories":["cs.AI"]}""")
        db.sourceBox.put(source)
        val savedSource = db.sourceBox.all.first()

        val result = ds.fetch(listOf(savedSource), createContext())

        assertEquals(1, result.articles.size)
        assertTrue(result.errors.isEmpty())
        assertEquals(SourceType.ARXIV_API, result.sourceType)
        assertEquals("Attention Is All You Need", result.articles[0].title)
        assertEquals(savedSource.id, result.articles[0].sourceId)
        assertTrue(result.articles[0].fetchedAt > 0)
    }

    @Test
    fun fetch_withHttpError_returnsError() = kotlinx.coroutines.runBlocking {
        val ds = createDataSource("Server Error", HttpStatusCode.InternalServerError)
        val source = Source(name = "arXiv", url = "", type = "ARXIV_API", config = """{"categories":["cs.AI"]}""")
        db.sourceBox.put(source)

        val result = ds.fetch(listOf(db.sourceBox.all.first()), createContext())

        assertTrue(result.articles.isEmpty())
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0].contains("HTTP 500"))
    }

    @Test
    fun fetch_skipsDuplicateArxivIds() = kotlinx.coroutines.runBlocking {
        db.articleBox.put(Article(title = "Existing", url = "https://arxiv.org/old", arxivId = "1706.03762"))
        val ds = createDataSource(SAMPLE_RESPONSE)
        val source = Source(name = "arXiv", url = "", type = "ARXIV_API", config = """{"categories":["cs.AI"]}""")
        db.sourceBox.put(source)

        val result = ds.fetch(listOf(db.sourceBox.all.first()), createContext())

        assertTrue(result.articles.isEmpty())
    }

    @Test
    fun fetch_respectsBudget() = kotlinx.coroutines.runBlocking {
        val ds = createDataSource(MULTI_ENTRY_RESPONSE)
        val source = Source(name = "arXiv", url = "", type = "ARXIV_API", config = """{"categories":["cs.AI"]}""")
        db.sourceBox.put(source)

        val result = ds.fetch(listOf(db.sourceBox.all.first()), createContext(budget = 1))

        assertEquals(1, result.articles.size)
    }

    @Test
    fun fetch_persistsArticlesToDb() = kotlinx.coroutines.runBlocking {
        val ds = createDataSource(SAMPLE_RESPONSE)
        val source = Source(name = "arXiv", url = "", type = "ARXIV_API", config = """{"categories":["cs.AI"]}""")
        db.sourceBox.put(source)

        ds.fetch(listOf(db.sourceBox.all.first()), createContext())

        val stored = db.articleBox.all
        assertEquals(1, stored.size)
        assertEquals("1706.03762", stored[0].arxivId)
    }

    @Test
    fun fetch_updatesSourceLastFetchedAt() = kotlinx.coroutines.runBlocking {
        val ds = createDataSource(SAMPLE_RESPONSE)
        val source = Source(name = "arXiv", url = "", type = "ARXIV_API", config = """{"categories":["cs.AI"]}""")
        db.sourceBox.put(source)
        val savedSource = db.sourceBox.all.first()
        assertEquals(0L, savedSource.lastFetchedAt)

        ds.fetch(listOf(savedSource), createContext())

        val updated = db.sourceBox.get(savedSource.id)
        assertTrue(updated.lastFetchedAt > 0)
    }

    companion object {
        private val SAMPLE_RESPONSE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom"
                  xmlns:arxiv="http://arxiv.org/schemas/atom">
              <title>ArXiv Query</title>
              <entry>
                <id>http://arxiv.org/abs/1706.03762v7</id>
                <title>Attention Is All You Need</title>
                <summary>We propose a new simple network architecture</summary>
                <published>2017-06-12T17:57:34Z</published>
                <author><name>Ashish Vaswani</name></author>
                <author><name>Noam Shazeer</name></author>
                <link rel="alternate" href="https://arxiv.org/abs/1706.03762v7"/>
                <link rel="related" href="https://arxiv.org/pdf/1706.03762v7" title="pdf"/>
                <arxiv:doi>10.1234/test.doi</arxiv:doi>
                <arxiv:primary_category term="cs.CL"/>
              </entry>
            </feed>
        """.trimIndent()

        private val MULTI_ENTRY_RESPONSE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom"
                  xmlns:arxiv="http://arxiv.org/schemas/atom">
              <title>ArXiv Query</title>
              <entry>
                <id>http://arxiv.org/abs/2401.00001v1</id>
                <title>Paper One</title>
                <summary>Summary one</summary>
                <published>2024-01-01T00:00:00Z</published>
                <author><name>Author A</name></author>
                <link rel="alternate" href="https://arxiv.org/abs/2401.00001v1"/>
              </entry>
              <entry>
                <id>http://arxiv.org/abs/2401.00002v1</id>
                <title>Paper Two</title>
                <summary>Summary two</summary>
                <published>2024-01-02T00:00:00Z</published>
                <author><name>Author B</name></author>
                <link rel="alternate" href="https://arxiv.org/abs/2401.00002v1"/>
              </entry>
            </feed>
        """.trimIndent()

        private val EMPTY_RESPONSE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>ArXiv Query</title>
            </feed>
        """.trimIndent()
    }
}
