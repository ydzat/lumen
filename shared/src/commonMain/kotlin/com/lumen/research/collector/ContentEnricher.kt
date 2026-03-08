package com.lumen.research.collector

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.Article_
import com.lumen.core.database.entities.ArticleSection
import com.lumen.core.database.entities.ArticleSection_
import com.lumen.research.analyzer.DeepAnalysisService
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import net.dankito.readability4j.Readability4J

class ContentEnricher(
    private val httpClient: HttpClient,
    private val db: LumenDatabase,
) {

    suspend fun enrichAll(articles: List<Article>): Int {
        val toEnrich = articles.filter { needsEnrichment(it) }
        if (toEnrich.isEmpty()) return 0

        // Group by source type so different sources run in parallel
        // while same-source articles respect per-source rate limits
        val bySource = toEnrich.groupBy { it.sourceType }
        val semaphore = Semaphore(CONCURRENCY)

        return coroutineScope {
            bySource.map { (_, group) ->
                async {
                    var enriched = 0
                    for ((index, article) in group.withIndex()) {
                        if (index > 0) delay(rateLimitMs(article))
                        semaphore.withPermit {
                            try {
                                val result = enrichSingle(article)
                                if (result != null) enriched++
                            } catch (_: Exception) {
                                // Continue with next article on failure
                            }
                        }
                    }
                    enriched
                }
            }.sumOf { it.await() }
        }
    }

    suspend fun enrichSingle(article: Article): Article? {
        val fetchUrl = resolveEnrichmentUrl(article) ?: return null

        val html = try {
            val response = httpClient.get(fetchUrl) {
                header("User-Agent", USER_AGENT)
                header("Accept", "text/html")
            }
            if (!response.status.isSuccess()) return null
            response.bodyAsText()
        } catch (_: Exception) {
            return null
        }

        val extractedContent = extractContent(fetchUrl, html) ?: return null
        if (extractedContent.length < MIN_CONTENT_LENGTH) return null

        val updated = article.copy(content = extractedContent)
        db.articleBox.put(updated)

        // Split HTML content into sections and persist for per-section features
        persistSections(updated.id, extractedContent)

        return updated
    }

    suspend fun processUnenriched(): Int {
        val unenriched = db.articleBox.query()
            .build()
            .use { it.find() }
            .filter { needsEnrichment(it) }

        return enrichAll(unenriched)
    }

    internal fun needsEnrichment(article: Article): Boolean {
        return article.content.length < CONTENT_THRESHOLD &&
            article.url.isNotBlank() &&
            article.sourceType in ENRICHABLE_SOURCE_TYPES
    }

    internal fun resolveEnrichmentUrl(article: Article): String? {
        if (article.url.isBlank()) return null
        return when (article.sourceType) {
            "ARXIV_API" -> resolveArxivHtmlUrl(article)
            else -> article.url
        }
    }

    internal fun resolveArxivHtmlUrl(article: Article): String? {
        val id = article.arxivId.takeIf { it.isNotBlank() }
            ?: ARXIV_ID_PATTERN.find(article.url)?.groupValues?.get(1)
            ?: return null
        return "$ARXIV_HTML_BASE/$id"
    }

    internal fun extractContent(url: String, html: String): String? {
        return try {
            // Extract table figures from original HTML before Readability4J strips them.
            // Readability4J removes <table> elements inside <figure> tags, losing
            // scientific tables (e.g. arXiv's <figure class="ltx_table">).
            val originalTableFigures = extractTableFigures(html)

            val readability = Readability4J(url, html)
            val parsed = readability.parse()
            var content = parsed.content?.takeIf { it.isNotBlank() } ?: return null

            // Re-inject stripped table content into empty figure shells
            if (originalTableFigures.isNotEmpty()) {
                content = restoreTableFigures(content, originalTableFigures)
            }

            content
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extracts full <figure> blocks that contain <table> from the original HTML.
     * Returns a map of figure id -> full figure HTML (with table intact).
     */
    internal fun extractTableFigures(html: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (match in FIGURE_WITH_TABLE_PATTERN.findAll(html)) {
            val figureHtml = match.value
            val id = FIGURE_ID_PATTERN.find(figureHtml)?.groupValues?.get(1) ?: continue
            // Only include figures that actually contain a <table>
            if (figureHtml.contains("<table", ignoreCase = true)) {
                result[id] = figureHtml
            }
        }
        return result
    }

    /**
     * Replaces empty figure shells (where Readability4J stripped the table)
     * with the original complete figure+table HTML.
     */
    internal fun restoreTableFigures(content: String, originals: Map<String, String>): String {
        var result = content
        for ((id, originalFigure) in originals) {
            // Readability4J leaves behind: <figure id="X"><figcaption>...</figcaption></figure>
            // Replace the empty shell with the original full figure
            val shellPattern = Regex(
                """<figure\s[^>]*id\s*=\s*"${Regex.escape(id)}"[^>]*>.*?</figure>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
            )
            result = shellPattern.replace(result, originalFigure)
        }
        return result
    }

    internal fun persistSections(articleId: Long, htmlContent: String) {
        val localSections = DeepAnalysisService.splitIntoSectionsStatic(htmlContent)
        if (localSections.isEmpty()) return

        // Remove any existing sections for this article
        val existing = db.articleSectionBox.query()
            .equal(ArticleSection_.articleId, articleId)
            .build()
            .use { it.find() }
        if (existing.isNotEmpty()) {
            db.articleSectionBox.remove(existing)
        }

        val entities = localSections.mapIndexed { index, s ->
            ArticleSection(
                articleId = articleId,
                sectionIndex = index,
                heading = s.heading,
                content = s.content,
                level = s.level,
                isKeySection = true,
            )
        }
        db.articleSectionBox.put(entities)
    }

    private fun rateLimitMs(article: Article): Long {
        return if (article.sourceType == "ARXIV_API") ARXIV_RATE_LIMIT_MS else RATE_LIMIT_MS
    }

    companion object {
        internal const val CONCURRENCY = 4
        internal const val RATE_LIMIT_MS = 1000L
        internal const val ARXIV_RATE_LIMIT_MS = 3000L
        internal const val CONTENT_THRESHOLD = 200
        internal const val MIN_CONTENT_LENGTH = 100
        internal const val USER_AGENT =
            "Mozilla/5.0 (compatible; Lumen/1.0; +https://github.com/ydzat/lumen)"
        internal const val ARXIV_HTML_BASE = "https://arxiv.org/html"

        internal val ENRICHABLE_SOURCE_TYPES = setOf("RSS", "ARXIV_API")

        private val ARXIV_ID_PATTERN = Regex("""arxiv\.org/abs/(\d+\.\d+)""")

        // Matches <figure ...> blocks that contain <table> (greedy but bounded by </figure>)
        // Uses a non-greedy match to handle nested structures
        private val FIGURE_WITH_TABLE_PATTERN = Regex(
            """<figure\b[^>]*>(?:(?!</figure>).)*<table\b.*?</table>.*?</figure>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        private val FIGURE_ID_PATTERN = Regex("""id\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
    }
}
