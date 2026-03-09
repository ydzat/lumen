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

class ScholarDataSourceTest {

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "objectbox-scholar-test-${System.nanoTime()}")
        tempDir.mkdirs()
        val store = MyObjectBox.builder().baseDirectory(tempDir).build()
        db = LumenDatabase(store)
    }

    @AfterTest
    fun teardown() {
        db.close()
        tempDir.deleteRecursively()
    }

    private fun createDataSource(
        responseJson: String,
        statusCode: HttpStatusCode = HttpStatusCode.OK,
    ): ScholarDataSource {
        val mockEngine = MockEngine {
            respond(
                content = responseJson,
                status = statusCode,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return ScholarDataSource(db, HttpClient(mockEngine))
    }

    private fun createContext(
        keywords: Set<String> = setOf("transformer"),
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
    fun parseResponse_extractsAllFields() {
        val ds = createDataSource(SAMPLE_RESPONSE)
        val response = ds.parseResponse(SAMPLE_RESPONSE)

        assertEquals(1, response.total)
        assertEquals(1, response.data.size)
        val paper = response.data[0]
        assertEquals("Attention Is All You Need", paper.title)
        assertEquals("The dominant sequence transduction models", paper.abstract)
        assertEquals(2, paper.authors.size)
        assertEquals("Ashish Vaswani", paper.authors[0].name)
        assertEquals(120000, paper.citationCount)
        assertEquals(15000, paper.influentialCitationCount)
        assertEquals("10.5555/3295222.3295349", paper.externalIds?.DOI)
        assertEquals("1706.03762", paper.externalIds?.ArXiv)
    }

    @Test
    fun mapToArticle_mapsAllFields() {
        val ds = createDataSource("")
        val paper = ScholarPaper(
            paperId = "abc123",
            title = "Test Paper",
            abstract = "Test abstract",
            url = "https://semanticscholar.org/paper/abc123",
            publicationDate = "2024-01-15",
            citationCount = 42,
            influentialCitationCount = 5,
            authors = listOf(ScholarAuthor("Alice"), ScholarAuthor("Bob")),
            externalIds = ScholarExternalIds(DOI = "10.1234/test", ArXiv = "2401.00001"),
        )

        val article = ds.mapToArticle(paper)!!

        assertEquals("Test Paper", article.title)
        assertEquals("Test abstract", article.summary)
        assertEquals("Alice, Bob", article.author)
        assertEquals("https://semanticscholar.org/paper/abc123", article.url)
        assertEquals("10.1234/test", article.doi)
        assertEquals("2401.00001", article.arxivId)
        assertEquals(42, article.citationCount)
        assertEquals(5, article.influentialCitationCount)
        assertEquals("SEMANTIC_SCHOLAR", article.sourceType)
        assertTrue(article.publishedAt > 0)
    }

    @Test
    fun mapToArticle_withBlankTitle_returnsNull() {
        val ds = createDataSource("")
        val paper = ScholarPaper(title = "", url = "https://example.com")

        val article = ds.mapToArticle(paper)

        assertEquals(null, article)
    }

    @Test
    fun mapToArticle_withNoUrl_generatesFallback() {
        val ds = createDataSource("")
        val paper = ScholarPaper(paperId = "abc123", title = "Paper", url = "")

        val article = ds.mapToArticle(paper)!!

        assertEquals("https://www.semanticscholar.org/paper/abc123", article.url)
    }

    @Test
    fun mapToArticle_withNoUrlAndNoPaperId_returnsNull() {
        val ds = createDataSource("")
        val paper = ScholarPaper(title = "Paper", url = "", paperId = "")

        val article = ds.mapToArticle(paper)

        assertEquals(null, article)
    }

    @Test
    fun mapToArticle_withNullExternalIds_defaultsToEmpty() {
        val ds = createDataSource("")
        val paper = ScholarPaper(paperId = "abc", title = "Paper", url = "https://example.com")

        val article = ds.mapToArticle(paper)!!

        assertEquals("", article.doi)
        assertEquals("", article.arxivId)
    }

    @Test
    fun parsePublicationDate_validDate() {
        val millis = ScholarDataSource.parsePublicationDate("2024-01-15")
        assertTrue(millis > 0)
    }

    @Test
    fun parsePublicationDate_invalidDate_returnsZero() {
        assertEquals(0L, ScholarDataSource.parsePublicationDate(null))
        assertEquals(0L, ScholarDataSource.parsePublicationDate(""))
        assertEquals(0L, ScholarDataSource.parsePublicationDate("not-a-date"))
    }

    @Test
    fun parseConfig_validJson() {
        val config = ScholarDataSource.parseConfig("""{"maxResults":25}""")
        assertEquals(25, config?.maxResults)
    }

    @Test
    fun parseConfig_emptyString_returnsNull() {
        assertEquals(null, ScholarDataSource.parseConfig(""))
    }

    @Test
    fun buildSearchUrl_constructsCorrectUrl() {
        val ds = createDataSource("")
        val url = ds.buildSearchUrl("transformer attention", 50)

        assertTrue(url.startsWith(ScholarDataSource.BASE_URL))
        assertTrue(url.contains("query=transformer+attention"))
        assertTrue(url.contains("limit=50"))
        assertTrue(url.contains("fields="))
    }

    @Test
    fun fetch_withValidResponse_returnsArticles() = kotlinx.coroutines.runBlocking {
        val ds = createDataSource(SAMPLE_RESPONSE)
        val source = Source(name = "Scholar", url = "", type = "SEMANTIC_SCHOLAR")
        db.sourceBox.put(source)
        val savedSource = db.sourceBox.all.first()

        val result = ds.fetch(listOf(savedSource), createContext())

        assertEquals(1, result.articles.size)
        assertTrue(result.errors.isEmpty())
        assertEquals(SourceType.SEMANTIC_SCHOLAR, result.sourceType)
        assertEquals("Attention Is All You Need", result.articles[0].title)
        assertEquals(savedSource.id, result.articles[0].sourceId)
        assertTrue(result.articles[0].fetchedAt > 0)
    }

    @Test
    fun fetch_withEmptyKeywords_returnsEmpty() = kotlinx.coroutines.runBlocking {
        val ds = createDataSource(SAMPLE_RESPONSE)
        val source = Source(name = "Scholar", url = "", type = "SEMANTIC_SCHOLAR")
        db.sourceBox.put(source)

        val result = ds.fetch(listOf(db.sourceBox.all.first()), createContext(keywords = emptySet()))

        assertTrue(result.articles.isEmpty())
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun fetch_withHttpError_returnsError() = kotlinx.coroutines.runBlocking {
        val ds = createDataSource("error", HttpStatusCode.TooManyRequests)
        val source = Source(name = "Scholar", url = "", type = "SEMANTIC_SCHOLAR")
        db.sourceBox.put(source)

        val result = ds.fetch(listOf(db.sourceBox.all.first()), createContext())

        assertTrue(result.articles.isEmpty())
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0].contains("HTTP 429"))
    }

    @Test
    fun fetch_skipsDuplicateUrls() = kotlinx.coroutines.runBlocking {
        db.articleBox.put(Article(title = "Existing", url = "https://semanticscholar.org/paper/abc123"))
        val ds = createDataSource(SAMPLE_RESPONSE)
        val source = Source(name = "Scholar", url = "", type = "SEMANTIC_SCHOLAR")
        db.sourceBox.put(source)

        val result = ds.fetch(listOf(db.sourceBox.all.first()), createContext())

        assertTrue(result.articles.isEmpty())
    }

    @Test
    fun fetch_skipsDuplicateDois() = kotlinx.coroutines.runBlocking {
        db.articleBox.put(Article(title = "Existing", url = "https://other.com", doi = "10.5555/3295222.3295349"))
        val ds = createDataSource(SAMPLE_RESPONSE)
        val source = Source(name = "Scholar", url = "", type = "SEMANTIC_SCHOLAR")
        db.sourceBox.put(source)

        val result = ds.fetch(listOf(db.sourceBox.all.first()), createContext())

        assertTrue(result.articles.isEmpty())
    }

    @Test
    fun fetch_skipsDuplicateArxivIds() = kotlinx.coroutines.runBlocking {
        db.articleBox.put(Article(title = "Existing", url = "https://other.com", arxivId = "1706.03762"))
        val ds = createDataSource(SAMPLE_RESPONSE)
        val source = Source(name = "Scholar", url = "", type = "SEMANTIC_SCHOLAR")
        db.sourceBox.put(source)

        val result = ds.fetch(listOf(db.sourceBox.all.first()), createContext())

        assertTrue(result.articles.isEmpty())
    }

    @Test
    fun fetch_respectsBudget() = kotlinx.coroutines.runBlocking {
        val ds = createDataSource(MULTI_PAPER_RESPONSE)
        val source = Source(name = "Scholar", url = "", type = "SEMANTIC_SCHOLAR")
        db.sourceBox.put(source)

        val result = ds.fetch(listOf(db.sourceBox.all.first()), createContext(budget = 1))

        assertEquals(1, result.articles.size)
    }

    @Test
    fun fetch_persistsArticlesToDb() = kotlinx.coroutines.runBlocking {
        val ds = createDataSource(SAMPLE_RESPONSE)
        val source = Source(name = "Scholar", url = "", type = "SEMANTIC_SCHOLAR")
        db.sourceBox.put(source)

        ds.fetch(listOf(db.sourceBox.all.first()), createContext())

        val stored = db.articleBox.all
        assertEquals(1, stored.size)
        assertEquals(120000, stored[0].citationCount)
    }

    @Test
    fun fetch_updatesSourceLastFetchedAt() = kotlinx.coroutines.runBlocking {
        val ds = createDataSource(SAMPLE_RESPONSE)
        val source = Source(name = "Scholar", url = "", type = "SEMANTIC_SCHOLAR")
        db.sourceBox.put(source)
        val savedSource = db.sourceBox.all.first()

        ds.fetch(listOf(savedSource), createContext())

        val updated = db.sourceBox.get(savedSource.id)
        assertTrue(updated.lastFetchedAt > 0)
    }

    companion object {
        private val SAMPLE_RESPONSE = """
            {
              "total": 1,
              "offset": 0,
              "data": [
                {
                  "paperId": "abc123",
                  "title": "Attention Is All You Need",
                  "abstract": "The dominant sequence transduction models",
                  "url": "https://semanticscholar.org/paper/abc123",
                  "year": 2017,
                  "publicationDate": "2017-06-12",
                  "citationCount": 120000,
                  "influentialCitationCount": 15000,
                  "authors": [
                    {"name": "Ashish Vaswani"},
                    {"name": "Noam Shazeer"}
                  ],
                  "externalIds": {
                    "DOI": "10.5555/3295222.3295349",
                    "ArXiv": "1706.03762"
                  }
                }
              ]
            }
        """.trimIndent()

        private val MULTI_PAPER_RESPONSE = """
            {
              "total": 2,
              "offset": 0,
              "data": [
                {
                  "paperId": "paper1",
                  "title": "Paper One",
                  "url": "https://semanticscholar.org/paper/paper1",
                  "citationCount": 10,
                  "influentialCitationCount": 1,
                  "authors": [{"name": "Author A"}]
                },
                {
                  "paperId": "paper2",
                  "title": "Paper Two",
                  "url": "https://semanticscholar.org/paper/paper2",
                  "citationCount": 20,
                  "influentialCitationCount": 2,
                  "authors": [{"name": "Author B"}]
                }
              ]
            }
        """.trimIndent()
    }
}
