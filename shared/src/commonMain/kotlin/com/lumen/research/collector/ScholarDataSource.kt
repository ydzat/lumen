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
import java.time.LocalDate
import java.time.ZoneOffset

class ScholarDataSource(
    private val db: LumenDatabase,
    private val httpClient: HttpClient,
) : DataSource {

    override val type: SourceType = SourceType.SEMANTIC_SCHOLAR
    override val displayName: String = "Semantic Scholar"

    override suspend fun fetch(sources: List<Source>, context: FetchContext): DataFetchResult {
        val allArticles = mutableListOf<Article>()
        val errors = mutableListOf<String>()
        var remainingBudget = context.remainingBudget

        val keywords = context.keywords
        if (keywords.isEmpty()) {
            return DataFetchResult(emptyList(), emptyList(), SourceType.SEMANTIC_SCHOLAR)
        }

        val existingUrls = queryExistingUrls()
        val existingDois = queryExistingDois()

        for ((index, source) in sources.withIndex()) {
            if (remainingBudget <= 0) break
            if (index > 0) delay(RATE_LIMIT_MS)

            try {
                val config = parseConfig(source.config)
                val limit = remainingBudget.coerceAtMost(config?.maxResults ?: MAX_RESULTS_DEFAULT)
                    .coerceAtMost(API_MAX_LIMIT)
                val query = keywords.joinToString(" ")
                val url = buildSearchUrl(query, limit)

                val response = httpClient.get(url)
                if (!response.status.isSuccess()) {
                    errors.add("Semantic Scholar error for ${source.name}: HTTP ${response.status.value}")
                    continue
                }

                val body = response.bodyAsText()
                val searchResponse = parseResponse(body)
                val now = System.currentTimeMillis()

                val newArticles = searchResponse.data
                    .mapNotNull { paper -> mapToArticle(paper) }
                    .filter { it.url !in existingUrls && (it.doi.isBlank() || it.doi !in existingDois) }
                    .take(remainingBudget)
                    .map { it.copy(sourceId = source.id, fetchedAt = now) }

                if (newArticles.isNotEmpty()) {
                    db.articleBox.put(newArticles)
                    newArticles.forEach { article ->
                        existingUrls.add(article.url)
                        if (article.doi.isNotBlank()) existingDois.add(article.doi)
                    }
                }
                db.sourceBox.put(source.copy(lastFetchedAt = now))

                allArticles.addAll(newArticles)
                remainingBudget -= newArticles.size
            } catch (e: Exception) {
                errors.add("Scholar ${source.name}: ${e.message ?: e::class.simpleName}")
            }
        }

        return DataFetchResult(allArticles, errors, SourceType.SEMANTIC_SCHOLAR)
    }

    internal fun buildSearchUrl(query: String, limit: Int): String {
        val encodedQuery = query.replace(" ", "+")
        return "$BASE_URL?query=$encodedQuery&offset=0&limit=$limit&fields=$FIELDS"
    }

    internal fun parseResponse(jsonBody: String): ScholarSearchResponse {
        return json.decodeFromString<ScholarSearchResponse>(jsonBody)
    }

    internal fun mapToArticle(paper: ScholarPaper): Article? {
        if (paper.title.isBlank()) return null
        val paperUrl = paper.url.ifBlank {
            if (paper.paperId.isNotBlank()) "https://www.semanticscholar.org/paper/${paper.paperId}" else return null
        }

        return Article(
            title = paper.title.trim(),
            url = paperUrl,
            summary = paper.abstract?.trim() ?: "",
            author = paper.authors.joinToString(", ") { it.name.trim() },
            publishedAt = parsePublicationDate(paper.publicationDate),
            doi = paper.externalIds?.DOI?.trim() ?: "",
            arxivId = paper.externalIds?.ArXiv?.trim() ?: "",
            citationCount = paper.citationCount,
            influentialCitationCount = paper.influentialCitationCount,
            sourceType = "SEMANTIC_SCHOLAR",
        )
    }

    private fun queryExistingUrls(): MutableSet<String> {
        return db.articleBox.query()
            .notEqual(Article_.url, "", StringOrder.CASE_SENSITIVE)
            .build()
            .use { it.find() }
            .mapTo(mutableSetOf()) { it.url }
    }

    private fun queryExistingDois(): MutableSet<String> {
        return db.articleBox.query()
            .notEqual(Article_.doi, "", StringOrder.CASE_SENSITIVE)
            .build()
            .use { it.find() }
            .mapTo(mutableSetOf()) { it.doi }
    }

    companion object {
        const val BASE_URL = "https://api.semanticscholar.org/graph/v1/paper/search"
        const val RATE_LIMIT_MS = 3000L
        const val MAX_RESULTS_DEFAULT = 50
        const val API_MAX_LIMIT = 100

        private const val FIELDS = "title,abstract,authors,url,year,publicationDate,citationCount,influentialCitationCount,externalIds"

        private val json = Json { ignoreUnknownKeys = true }

        internal fun parsePublicationDate(dateStr: String?): Long {
            if (dateStr.isNullOrBlank()) return 0L
            return try {
                LocalDate.parse(dateStr).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            } catch (_: Exception) {
                0L
            }
        }

        internal fun parseConfig(configJson: String): ScholarSourceConfig? {
            if (configJson.isBlank()) return null
            return try {
                json.decodeFromString<ScholarSourceConfig>(configJson)
            } catch (_: Exception) {
                null
            }
        }
    }
}

@Serializable
data class ScholarSourceConfig(
    val maxResults: Int = ScholarDataSource.MAX_RESULTS_DEFAULT,
)

@Serializable
data class ScholarSearchResponse(
    val total: Int = 0,
    val offset: Int = 0,
    val data: List<ScholarPaper> = emptyList(),
)

@Serializable
data class ScholarPaper(
    val paperId: String = "",
    val title: String = "",
    val abstract: String? = null,
    val url: String = "",
    val year: Int? = null,
    val publicationDate: String? = null,
    val citationCount: Int = 0,
    val influentialCitationCount: Int = 0,
    val authors: List<ScholarAuthor> = emptyList(),
    val externalIds: ScholarExternalIds? = null,
)

@Serializable
data class ScholarAuthor(val name: String = "")

@Serializable
data class ScholarExternalIds(
    val DOI: String? = null,
    val ArXiv: String? = null,
)
