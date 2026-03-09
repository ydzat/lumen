package com.lumen.research.collector

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.Article_
import com.lumen.core.database.entities.Source
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory

class ArxivApiDataSource(
    private val db: LumenDatabase,
    private val httpClient: HttpClient,
) : DataSource {

    override val type: SourceType = SourceType.ARXIV_API
    override val displayName: String = "arXiv"

    override suspend fun fetch(sources: List<Source>, context: FetchContext): DataFetchResult {
        val allArticles = mutableListOf<Article>()
        val errors = mutableListOf<String>()
        val failedIds = mutableSetOf<Long>()
        var remainingBudget = context.remainingBudget
        val existingArxivIds = queryExistingArxivIds()

        val perSourceCap = if (sources.isNotEmpty()) {
            (context.remainingBudget / sources.size).coerceAtLeast(MIN_PER_SOURCE)
        } else {
            context.remainingBudget
        }

        for ((index, source) in sources.withIndex()) {
            if (remainingBudget <= 0) break
            if (index > 0) delay(RATE_LIMIT_MS)

            try {
                val limit = remainingBudget.coerceAtMost(perSourceCap)
                val url = buildQueryUrl(source, context, limit)
                val response = httpClient.get(url)
                if (!response.status.isSuccess()) {
                    errors.add("arXiv API error for ${source.name}: HTTP ${response.status.value}")
                    failedIds.add(source.id)
                    continue
                }

                val xml = response.bodyAsText()
                val parsed = parseAtomResponse(xml)
                val now = System.currentTimeMillis()

                val newArticles = parsed.filter { it.arxivId !in existingArxivIds }
                    .take(limit)
                    .map { it.copy(sourceId = source.id, fetchedAt = now) }

                if (newArticles.isNotEmpty()) {
                    db.articleBox.put(newArticles)
                    newArticles.forEach { existingArxivIds.add(it.arxivId) }
                }
                db.sourceBox.put(source.copy(lastFetchedAt = now))

                allArticles.addAll(newArticles)
                remainingBudget -= newArticles.size
            } catch (e: Exception) {
                errors.add("arXiv ${source.name}: ${e.message ?: e::class.simpleName}")
                failedIds.add(source.id)
            }
        }

        return DataFetchResult(allArticles, errors, SourceType.ARXIV_API, failedIds)
    }

    internal fun buildQueryUrl(source: Source, context: FetchContext, maxResults: Int): String {
        val config = parseConfig(source.config)
        val queryParts = mutableListOf<String>()

        // 1. Category filter: from config, or extracted from source URL, or default
        val categories = config?.categories ?: emptyList()
        if (categories.isNotEmpty()) {
            val catQuery = categories.joinToString("+OR+") { "cat:$it" }
            queryParts.add("($catQuery)")
        } else {
            val urlCategory = extractCategoryFromUrl(source.url)
            if (urlCategory != null) {
                queryParts.add("(cat:$urlCategory)")
            }
        }

        // 2. Keyword filter: sanitize multi-word keywords for arXiv query syntax
        val keywords = context.keywords
        if (keywords.isNotEmpty()) {
            val sanitized = keywords
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { kw ->
                    // Replace spaces with _ for arXiv query syntax (multi-word terms)
                    val escaped = kw.replace(" ", "_")
                    "all:$escaped"
                }
            if (sanitized.isNotEmpty()) {
                queryParts.add("(${sanitized.joinToString("+OR+")})")
            }
        }

        // 3. Fallback: use source URL directly or default category
        if (queryParts.isEmpty()) {
            val sourceUrl = source.url
            if (sourceUrl.startsWith("http")) return sourceUrl
            queryParts.add("cat:cs.AI")
        }

        val searchQuery = queryParts.joinToString("+AND+")
        val limit = maxResults.coerceAtMost(config?.maxResults ?: MAX_RESULTS_DEFAULT)
        return "$BASE_URL?search_query=$searchQuery&start=0&max_results=$limit&sortBy=submittedDate&sortOrder=descending"
    }

    internal fun parseAtomResponse(xml: String): List<Article> {
        val builder = xmlFactory.newDocumentBuilder()
        val document = builder.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        val entries = document.getElementsByTagNameNS(ATOM_NS, "entry")
        return (0 until entries.length).mapNotNull { i ->
            parseEntry(entries.item(i) as Element)
        }
    }

    private fun parseEntry(entry: Element): Article? {
        val title = getTextContent(entry, ATOM_NS, "title")?.trim()?.replace(WHITESPACE_PATTERN, " ")
            ?: return null

        val id = getTextContent(entry, ATOM_NS, "id") ?: return null
        val arxivId = extractArxivId(id)

        val summary = getTextContent(entry, ATOM_NS, "summary")?.trim() ?: ""
        val authors = getAuthors(entry)
        val published = getTextContent(entry, ATOM_NS, "published")
        val publishedAt = parseIso8601(published)
        val doi = getTextContent(entry, ARXIV_NS, "doi")?.trim() ?: ""
        val url = getAlternateLink(entry) ?: id

        return Article(
            title = title,
            url = url,
            summary = summary,
            author = authors,
            publishedAt = publishedAt,
            arxivId = arxivId,
            doi = doi,
            sourceType = "ARXIV_API",
        )
    }

    private fun getTextContent(parent: Element, ns: String, tag: String): String? {
        val nodes = parent.getElementsByTagNameNS(ns, tag)
        if (nodes.length == 0) return null
        return nodes.item(0).textContent
    }

    private fun getAuthors(entry: Element): String {
        val authorNodes = entry.getElementsByTagNameNS(ATOM_NS, "author")
        val names = mutableListOf<String>()
        for (i in 0 until authorNodes.length) {
            val authorEl = authorNodes.item(i) as Element
            val name = getTextContent(authorEl, ATOM_NS, "name")
            if (name != null) names.add(name.trim())
        }
        return names.joinToString(", ")
    }

    private fun getAlternateLink(entry: Element): String? {
        val links = entry.getElementsByTagNameNS(ATOM_NS, "link")
        for (i in 0 until links.length) {
            val link = links.item(i) as Element
            if (link.getAttribute("rel") == "alternate") {
                return link.getAttribute("href")
            }
        }
        return null
    }

    private fun queryExistingArxivIds(): MutableSet<String> {
        return db.articleBox.query()
            .notEqual(Article_.arxivId, "", StringOrder.CASE_SENSITIVE)
            .build()
            .use { it.find() }
            .mapTo(mutableSetOf()) { it.arxivId }
    }

    companion object {
        const val BASE_URL = "https://export.arxiv.org/api/query"
        const val RATE_LIMIT_MS = 3000L
        const val MIN_PER_SOURCE = 5
        const val MAX_RESULTS_DEFAULT = 50

        private const val ATOM_NS = "http://www.w3.org/2005/Atom"
        private const val ARXIV_NS = "http://arxiv.org/schemas/atom"

        private val CATEGORY_PATTERN = Regex("""cat:([a-zA-Z-]+\.[a-zA-Z-]+)""")
        private val ARXIV_ID_PATTERN = Regex("""arxiv\.org/abs/(\d+\.\d+)(v\d+)?""")
        private val VERSION_SUFFIX_PATTERN = Regex("v\\d+$")
        private val WHITESPACE_PATTERN = Regex("\\s+")

        private val xmlFactory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // XXE prevention hardening.
            // JVM (Xerces): supports disallow-doctype-decl — the strictest guard.
            // Android (Harmony XML): does not support any of these features, but
            // is secure by default since API 24 (external entities disabled).
            val xxeFeatures = mapOf(
                "http://apache.org/xml/features/disallow-doctype-decl" to true,
                "http://xml.org/sax/features/external-general-entities" to false,
                "http://xml.org/sax/features/external-parameter-entities" to false,
                "http://javax.xml.XMLConstants/feature/secure-processing" to true,
            )
            for ((feature, value) in xxeFeatures) {
                try {
                    setFeature(feature, value)
                } catch (_: Exception) {
                    // Feature not supported on this XML parser implementation
                }
            }
        }

        private val json = Json { ignoreUnknownKeys = true }

        internal fun extractCategoryFromUrl(url: String): String? {
            return CATEGORY_PATTERN.find(url)?.groupValues?.get(1)
        }

        internal fun extractArxivId(idUrl: String): String {
            val match = ARXIV_ID_PATTERN.find(idUrl)
            return match?.groupValues?.get(1) ?: idUrl.substringAfterLast("/").replace(VERSION_SUFFIX_PATTERN, "")
        }

        internal fun parseIso8601(dateStr: String?): Long {
            if (dateStr.isNullOrBlank()) return 0L
            return try {
                Instant.parse(dateStr).toEpochMilli()
            } catch (_: Exception) {
                0L
            }
        }

        internal fun parseConfig(configJson: String): ArxivSourceConfig? {
            if (configJson.isBlank()) return null
            return try {
                json.decodeFromString<ArxivSourceConfig>(configJson)
            } catch (_: Exception) {
                null
            }
        }
    }
}

@Serializable
data class ArxivSourceConfig(
    val categories: List<String> = emptyList(),
    val maxResults: Int = ArxivApiDataSource.MAX_RESULTS_DEFAULT,
)
