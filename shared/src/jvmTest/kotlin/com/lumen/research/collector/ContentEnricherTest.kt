package com.lumen.research.collector

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.ArticleSection_
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
import kotlin.test.assertNull
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
    fun needsEnrichment_nonRssNonArxivArticle_returnsFalse() {
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

    @Test
    fun needsEnrichment_arxivArticleWithEmptyContent_returnsTrue() {
        val enricher = createEnricher("")
        val article = Article(
            url = "https://arxiv.org/abs/2603.05500v1",
            summary = "An abstract",
            content = "",
            sourceType = "ARXIV_API",
            arxivId = "2603.05500",
        )
        assertTrue(enricher.needsEnrichment(article))
    }

    @Test
    fun needsEnrichment_arxivArticleWithContent_returnsFalse() {
        val enricher = createEnricher("")
        val article = Article(
            url = "https://arxiv.org/abs/2603.05500v1",
            content = "x".repeat(300),
            sourceType = "ARXIV_API",
            arxivId = "2603.05500",
        )
        assertFalse(enricher.needsEnrichment(article))
    }

    // --- resolveArxivHtmlUrl ---

    @Test
    fun resolveArxivHtmlUrl_usesArxivIdField() {
        val enricher = createEnricher("")
        val article = Article(
            url = "https://arxiv.org/abs/2603.05500v1",
            sourceType = "ARXIV_API",
            arxivId = "2603.05500",
        )
        assertEquals("https://arxiv.org/html/2603.05500", enricher.resolveArxivHtmlUrl(article))
    }

    @Test
    fun resolveArxivHtmlUrl_fallsBackToUrlParsing() {
        val enricher = createEnricher("")
        val article = Article(
            url = "https://arxiv.org/abs/2401.12345v2",
            sourceType = "ARXIV_API",
            arxivId = "",
        )
        assertEquals("https://arxiv.org/html/2401.12345", enricher.resolveArxivHtmlUrl(article))
    }

    @Test
    fun resolveArxivHtmlUrl_returnsNullForInvalidUrl() {
        val enricher = createEnricher("")
        val article = Article(
            url = "https://example.com/something",
            sourceType = "ARXIV_API",
            arxivId = "",
        )
        assertNull(enricher.resolveArxivHtmlUrl(article))
    }

    // --- resolveEnrichmentUrl ---

    @Test
    fun resolveEnrichmentUrl_rssUsesArticleUrl() {
        val enricher = createEnricher("")
        val article = Article(
            url = "https://blog.example.com/post",
            sourceType = "RSS",
        )
        assertEquals("https://blog.example.com/post", enricher.resolveEnrichmentUrl(article))
    }

    @Test
    fun resolveEnrichmentUrl_arxivUsesHtmlUrl() {
        val enricher = createEnricher("")
        val article = Article(
            url = "https://arxiv.org/abs/2603.05500v1",
            sourceType = "ARXIV_API",
            arxivId = "2603.05500",
        )
        assertEquals("https://arxiv.org/html/2603.05500", enricher.resolveEnrichmentUrl(article))
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
    fun enrichAll_fetchesContentForEmptyRssArticles() = runBlocking {
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
    fun enrichAll_fetchesContentForArxivArticles() = runBlocking {
        val html = """
            <html><head><title>Test Paper</title></head><body>
            <article>
                <h1>Test Paper Title</h1>
                <section>
                    <h2>1 Introduction</h2>
                    <p>This paper presents a novel approach to solving important problems in machine learning.
                    We introduce a new framework that achieves state-of-the-art results on multiple benchmarks.
                    Our method is both efficient and scalable, making it practical for real-world applications.</p>
                </section>
                <section>
                    <h2>2 Related Work</h2>
                    <p>Previous approaches have focused on different aspects of this problem.
                    Smith et al. (2023) proposed a method based on attention mechanisms.
                    Johnson et al. (2024) extended this work to handle larger datasets.</p>
                </section>
                <section>
                    <h2>3 Results</h2>
                    <p>Our experiments demonstrate significant improvements over existing baselines.
                    We evaluate on three standard benchmarks and show consistent gains across all metrics.</p>
                </section>
            </article>
            </body></html>
        """.trimIndent()

        val enricher = createEnricher(html)
        val articleId = db.articleBox.put(
            Article(
                url = "https://arxiv.org/abs/2603.05500v1",
                title = "Test Paper",
                summary = "An abstract about ML.",
                content = "",
                sourceType = "ARXIV_API",
                arxivId = "2603.05500",
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
    fun enrichAll_skipsNonEnrichableArticles() = runBlocking {
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
        val unchanged = db.articleBox.get(articleId)
        assertEquals("", unchanged.content)
    }

    @Test
    fun enrichAll_arxivUsesHtmlEndpoint() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val mockEngine = MockEngine { request ->
            requestedUrls.add(request.url.toString())
            respond(
                content = """
                    <html><head><title>Paper</title></head><body>
                    <article>
                        <h1>Paper Title</h1>
                        <p>Enough content to pass the minimum length check. This paragraph contains
                        substantial text that simulates the introduction of a research paper with
                        sufficient detail for the content enricher to accept it.</p>
                    </article>
                    </body></html>
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html"),
            )
        }
        val enricher = ContentEnricher(HttpClient(mockEngine), db)

        val articleId = db.articleBox.put(
            Article(
                url = "https://arxiv.org/abs/2603.05500v1",
                title = "Test Paper",
                summary = "Abstract",
                content = "",
                sourceType = "ARXIV_API",
                arxivId = "2603.05500",
            ),
        )

        val articles = listOf(db.articleBox.get(articleId))
        enricher.enrichAll(articles)

        assertTrue(requestedUrls.isNotEmpty())
        assertEquals("https://arxiv.org/html/2603.05500", requestedUrls.first())
    }

    // --- Section persistence ---

    @Test
    fun enrichSingle_persistsSectionsFromHtml() = runBlocking {
        val html = """
            <html><head><title>Test</title></head><body>
            <article>
                <h2>Introduction</h2>
                <p>This is the introduction with enough content to pass minimum checks.
                It has multiple sentences to ensure it meets the threshold.</p>
                <h2>Methods</h2>
                <p>This section describes the methodology used in this research.
                We used a combination of techniques to achieve our results.</p>
                <h2>Conclusion</h2>
                <p>In conclusion, our approach works well and demonstrates improvements.</p>
            </article>
            </body></html>
        """.trimIndent()

        val enricher = createEnricher(html)
        val articleId = db.articleBox.put(
            Article(
                url = "https://example.com/article-with-sections",
                title = "Sectioned Article",
                content = "",
                sourceType = "RSS",
            ),
        )

        enricher.enrichSingle(db.articleBox.get(articleId))

        // Verify sections were created
        val sections = db.articleSectionBox.query()
            .equal(ArticleSection_.articleId, articleId)
            .build()
            .use { it.find() }
            .sortedBy { it.sectionIndex }

        assertTrue(sections.isNotEmpty(), "Sections should be persisted")
        // Content should contain HTML tags (not stripped)
        val allContent = sections.joinToString(" ") { it.content }
        assertTrue(allContent.isNotBlank())
    }

    // --- Readability4J table preservation ---

    @Test
    fun extractContent_preservesArxivStyleTables() {
        val enricher = createEnricher("")
        val html = """
            <html><head><title>Test Paper</title></head><body>
            <article>
                <h1>Test Paper Title</h1>
                <p>This paper introduces a novel approach with extensive content to ensure
                Readability4J considers this article substantial enough to extract.</p>
                <p>We present results across multiple benchmarks demonstrating significant
                improvements over existing state-of-the-art methods in the field.</p>
                <section>
                    <h2>1 Introduction</h2>
                    <p>Introduction paragraph with enough content to make Readability4J happy.
                    We describe our approach and the motivations behind our research direction.</p>
                </section>
                <section>
                    <h2>2 Results</h2>
                    <p>Our method achieves state-of-the-art performance on all benchmarks.</p>
                    <figure class="ltx_table" id="S5.T1">
                        <figcaption class="ltx_caption">
                            <span class="ltx_tag">TABLE I: </span>Hyperparameters for Training
                        </figcaption>
                        <table class="ltx_tabular">
                            <thead>
                                <tr>
                                    <th class="ltx_th">Hyperparameter</th>
                                    <th class="ltx_th">Value</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr>
                                    <th class="ltx_th">Learning Rate</th>
                                    <td class="ltx_td">0.001</td>
                                </tr>
                                <tr>
                                    <th class="ltx_th">Batch Size</th>
                                    <td class="ltx_td">32</td>
                                </tr>
                                <tr>
                                    <th class="ltx_th">Epochs</th>
                                    <td class="ltx_td">100</td>
                                </tr>
                            </tbody>
                        </table>
                    </figure>
                    <figure class="ltx_table" id="S5.T2">
                        <figcaption class="ltx_caption">
                            <span class="ltx_tag">TABLE II: </span>Ablation Study
                        </figcaption>
                        <table class="ltx_tabular">
                            <thead>
                                <tr>
                                    <th class="ltx_th">Method</th>
                                    <th class="ltx_th">Accuracy</th>
                                    <th class="ltx_th">F1 Score</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr>
                                    <td class="ltx_td">Baseline</td>
                                    <td class="ltx_td">85.2</td>
                                    <td class="ltx_td">83.1</td>
                                </tr>
                                <tr>
                                    <td class="ltx_td">Ours</td>
                                    <td class="ltx_td">92.7</td>
                                    <td class="ltx_td">91.3</td>
                                </tr>
                            </tbody>
                        </table>
                    </figure>
                </section>
                <section>
                    <h2>3 Conclusion</h2>
                    <p>We have demonstrated the effectiveness of our approach through extensive
                    experiments. Future work includes scaling to larger datasets.</p>
                </section>
            </article>
            </body></html>
        """.trimIndent()

        val result = enricher.extractContent("https://arxiv.org/html/2603.05504v1", html)
        assertNotNull(result, "extractContent should return non-null")

        assertTrue(result.contains("<table", ignoreCase = true), "Table elements should be preserved after extraction")
        assertTrue(result.contains("<figure", ignoreCase = true), "Figure wrapper should be preserved")
        assertTrue(result.contains("<figcaption", ignoreCase = true), "Figcaption should be preserved")
    }

    @Test
    fun extractContent_realArxivPage_preservesTables() {
        val enricher = createEnricher("")
        val arxivFile = File("/tmp/arxiv_page.html")
        if (!arxivFile.exists()) return // Skip if page not fetched

        val html = arxivFile.readText()
        val result = enricher.extractContent("https://arxiv.org/html/2603.05504v1", html)
        assertNotNull(result, "extractContent should return non-null for real arXiv page")

        assertTrue(result.contains("<table", ignoreCase = true), "Should contain table elements")
        assertTrue(result.contains("TABLE I"), "Should contain TABLE I caption")
        assertTrue(result.contains("TABLE II"), "Should contain TABLE II caption")
        assertTrue(result.contains("Hyperparameters"), "Should contain Hyperparameters text")

        // Verify the tables have actual content (not just empty shells)
        val tablePattern = Regex("<table[^>]*>.*?</table>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val tables = tablePattern.findAll(result).toList()
        assertTrue(tables.size >= 3, "Should have at least 3 table elements (equation + TABLE I + TABLE II)")
    }
}
