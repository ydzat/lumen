package com.lumen.research.collector

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.MyObjectBox
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class ContentEnricherTest {

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "objectbox-enricher-test-${System.nanoTime()}")
        tempDir.mkdirs()
        val store = MyObjectBox.builder().baseDirectory(tempDir).build()
        db = LumenDatabase(store)
    }

    @AfterTest
    fun teardown() {
        db.close()
        tempDir.deleteRecursively()
    }

    private fun createEnricher(
        responseHtml: String,
        statusCode: HttpStatusCode = HttpStatusCode.OK,
    ): ContentEnricher {
        val mockEngine = MockEngine {
            respond(
                content = responseHtml,
                status = statusCode,
                headers = headersOf(HttpHeaders.ContentType, "text/html"),
            )
        }
        return ContentEnricher(HttpClient(mockEngine), db)
    }

    // --- needsEnrichment ---

    @Test
    fun needsEnrichment_emptyContentRssArticle_returnsTrue() {
        val enricher = createEnricher("")
        val article = Article(
            url = "https://example.com/article",
            summary = "Short summary",
            content = "",
            sourceType = "RSS",
        )
        assertTrue(enricher.needsEnrichment(article))
    }

    @Test
    fun needsEnrichment_shortContentRssArticle_returnsTrue() {
        val enricher = createEnricher("")
        val article = Article(
            url = "https://example.com/article",
            content = "A brief description under 200 chars.",
            sourceType = "RSS",
        )
        assertTrue(enricher.needsEnrichment(article))
    }

    @Test
    fun needsEnrichment_longContentRssArticle_returnsFalse() {
        val enricher = createEnricher("")
        val article = Article(
            url = "https://example.com/article",
            content = "x".repeat(300),
            sourceType = "RSS",
        )
        assertFalse(enricher.needsEnrichment(article))
    }

    @Test
    fun needsEnrichment_nonRssArticle_returnsFalse() {
        val enricher = createEnricher("")
        val article = Article(
            url = "https://example.com/article",
            content = "",
            sourceType = "GITHUB_RELEASES",
        )
        assertFalse(enricher.needsEnrichment(article))
    }

    @Test
    fun needsEnrichment_noUrl_returnsFalse() {
        val enricher = createEnricher("")
        val article = Article(
            url = "",
            content = "",
            sourceType = "RSS",
        )
        assertFalse(enricher.needsEnrichment(article))
    }

    // --- extractContent ---

    @Test
    fun extractContent_preservesHeadingStructure() {
        val enricher = createEnricher("")
        val html = """
            <html><head><title>Test</title></head><body>
            <article>
                <h1>Main Title</h1>
                <p>Introduction paragraph with enough text to be meaningful content for extraction.</p>
                <h2>Section One</h2>
                <p>This is the first section with detailed content about the topic at hand.</p>
                <h2>Section Two</h2>
                <p>This is the second section with more detailed content about another aspect.</p>
                <p>Additional paragraph to ensure sufficient content length for extraction.</p>
            </article>
            </body></html>
        """.trimIndent()

        val result = enricher.extractContent("https://example.com/article", html)
        assertNotNull(result)
        assertTrue(result.contains("Section One") || result.contains("section", ignoreCase = true))
    }

    // --- enrichAll ---

    @Test
    fun enrichAll_fetchesContentForEmptyArticles() = runBlocking {
        val html = """
            <html><head><title>Test Article</title></head><body>
            <article>
                <h1>Full Article Title</h1>
                <p>This is a full article with enough content to pass the minimum length check.
                It contains multiple sentences and paragraphs to simulate a real article.
                The content extraction should preserve the structure of the article.</p>
                <h2>Section One</h2>
                <p>Detailed content for section one with technical details about the implementation.
                This paragraph provides additional context and information.</p>
                <h2>Section Two</h2>
                <p>Detailed content for section two with analysis and conclusions.
                This paragraph wraps up the discussion with final thoughts.</p>
            </article>
            </body></html>
        """.trimIndent()

        val enricher = createEnricher(html)
        val articleId = db.articleBox.put(
            Article(
                url = "https://example.com/test-article",
                title = "Test Article",
                summary = "Short summary",
                content = "",
                sourceType = "RSS",
            ),
        )

        val articles = listOf(db.articleBox.get(articleId))
        val count = enricher.enrichAll(articles)

        assertEquals(1, count)
        val updated = db.articleBox.get(articleId)
        assertTrue(updated.content.length > 100)
    }

    @Test
    fun enrichAll_skipsArticlesWithExistingContent() = runBlocking {
        var requestCount = 0
        val mockEngine = MockEngine {
            requestCount++
            respond(
                content = "<html><body><p>Content</p></body></html>",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html"),
            )
        }
        val enricher = ContentEnricher(HttpClient(mockEngine), db)

        val articleId = db.articleBox.put(
            Article(
                url = "https://example.com/test",
                title = "Test",
                content = "x".repeat(300),
                sourceType = "RSS",
            ),
        )

        val articles = listOf(db.articleBox.get(articleId))
        enricher.enrichAll(articles)

        assertEquals(0, requestCount)
    }

    @Test
    fun enrichAll_skipsNonRssArticles() = runBlocking {
        var requestCount = 0
        val mockEngine = MockEngine {
            requestCount++
            respond(
                content = "<html><body><p>Content</p></body></html>",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html"),
            )
        }
        val enricher = ContentEnricher(HttpClient(mockEngine), db)

        val articleId = db.articleBox.put(
            Article(
                url = "https://github.com/repo/releases/1",
                title = "Release v1.0",
                content = "",
                sourceType = "GITHUB_RELEASES",
            ),
        )

        val articles = listOf(db.articleBox.get(articleId))
        enricher.enrichAll(articles)

        assertEquals(0, requestCount)
    }

    @Test
    fun enrichAll_handlesHttpFailureGracefully() = runBlocking {
        val enricher = createEnricher("Not Found", HttpStatusCode.NotFound)

        val articleId = db.articleBox.put(
            Article(
                url = "https://example.com/missing",
                title = "Missing Article",
                content = "",
                sourceType = "RSS",
            ),
        )

        val articles = listOf(db.articleBox.get(articleId))
        val count = enricher.enrichAll(articles)

        assertEquals(0, count)
        // Original article content unchanged
        val unchanged = db.articleBox.get(articleId)
        assertEquals("", unchanged.content)
    }
}
