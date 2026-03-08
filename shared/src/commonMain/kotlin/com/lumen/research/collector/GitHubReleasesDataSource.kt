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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

class GitHubReleasesDataSource(
    private val db: LumenDatabase,
    private val httpClient: HttpClient,
) : DataSource {

    override val type: SourceType = SourceType.GITHUB_RELEASES
    override val displayName: String = "GitHub Releases"

    override suspend fun fetch(sources: List<Source>, context: FetchContext): DataFetchResult {
        val allArticles = mutableListOf<Article>()
        val errors = mutableListOf<String>()
        var remainingBudget = context.remainingBudget
        val existingUrls = queryExistingUrls()
        var requestCount = 0

        for (source in sources) {
            if (remainingBudget <= 0) break

            try {
                val config = parseConfig(source.config)
                val repos = config?.repos ?: emptyList()
                val includePrerelease = config?.includePrerelease ?: false
                val perRepoLimit = config?.maxReleasesPerRepo ?: MAX_PER_REPO_DEFAULT

                for (repo in repos) {
                    if (remainingBudget <= 0) break
                    if (requestCount > 0) delay(RATE_LIMIT_MS)
                    requestCount++

                    try {
                        val limit = remainingBudget.coerceAtMost(perRepoLimit)
                        val url = buildApiUrl(repo, limit)
                        val response = httpClient.get(url)

                        if (!response.status.isSuccess()) {
                            errors.add("GitHub Releases $repo: HTTP ${response.status.value}")
                            continue
                        }

                        val body = response.bodyAsText()
                        val releases = parseResponse(body)
                        val now = System.currentTimeMillis()

                        val newArticles = releases
                            .filter { !it.draft }
                            .filter { includePrerelease || !it.prerelease }
                            .mapNotNull { release -> mapToArticle(release, repo, source.id) }
                            .filter { it.url !in existingUrls }
                            .take(remainingBudget)
                            .map { it.copy(fetchedAt = now) }

                        if (newArticles.isNotEmpty()) {
                            db.articleBox.put(newArticles)
                            newArticles.forEach { existingUrls.add(it.url) }
                        }

                        allArticles.addAll(newArticles)
                        remainingBudget -= newArticles.size
                    } catch (e: Exception) {
                        errors.add("GitHub Releases $repo: ${e.message ?: e::class.simpleName}")
                    }
                }

                db.sourceBox.put(source.copy(lastFetchedAt = System.currentTimeMillis()))
            } catch (e: Exception) {
                errors.add("GitHub Releases ${source.name}: ${e.message ?: e::class.simpleName}")
            }
        }

        return DataFetchResult(allArticles, errors, SourceType.GITHUB_RELEASES)
    }

    internal fun buildApiUrl(repo: String, perPage: Int): String {
        return "$BASE_URL/repos/$repo/releases?per_page=$perPage"
    }

    internal fun parseResponse(jsonBody: String): List<GitHubRelease> {
        return json.decodeFromString<List<GitHubRelease>>(jsonBody)
    }

    internal fun mapToArticle(release: GitHubRelease, repo: String, sourceId: Long): Article? {
        if (release.htmlUrl.isBlank()) return null
        val releaseName = release.name?.takeIf { it.isNotBlank() } ?: release.tagName
        val body = release.body ?: ""

        return Article(
            sourceId = sourceId,
            title = "$repo: $releaseName",
            url = release.htmlUrl,
            summary = body.take(SUMMARY_MAX_LENGTH),
            content = body,
            author = release.author?.login ?: "",
            publishedAt = parseIso8601(release.publishedAt),
            sourceType = "GITHUB_RELEASES",
        )
    }

    private fun queryExistingUrls(): MutableSet<String> {
        return db.articleBox.query()
            .notEqual(Article_.url, "", StringOrder.CASE_SENSITIVE)
            .equal(Article_.sourceType, "GITHUB_RELEASES", StringOrder.CASE_SENSITIVE)
            .build()
            .use { it.find() }
            .mapTo(mutableSetOf()) { it.url }
    }

    companion object {
        internal const val BASE_URL = "https://api.github.com"
        internal const val RATE_LIMIT_MS = 1000L
        internal const val MAX_PER_REPO_DEFAULT = 10
        private const val SUMMARY_MAX_LENGTH = 500

        private val json = Json { ignoreUnknownKeys = true }

        internal fun parseIso8601(dateStr: String?): Long {
            if (dateStr.isNullOrBlank()) return 0L
            return try {
                Instant.parse(dateStr).toEpochMilli()
            } catch (_: Exception) {
                0L
            }
        }

        internal fun parseConfig(configJson: String): GitHubReleasesSourceConfig? {
            if (configJson.isBlank()) return null
            return try {
                json.decodeFromString<GitHubReleasesSourceConfig>(configJson)
            } catch (_: Exception) {
                null
            }
        }
    }
}

@Serializable
data class GitHubReleasesSourceConfig(
    val repos: List<String> = emptyList(),
    val includePrerelease: Boolean = false,
    val maxReleasesPerRepo: Int = GitHubReleasesDataSource.MAX_PER_REPO_DEFAULT,
)

@Serializable
data class GitHubRelease(
    val id: Long = 0,
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("published_at") val publishedAt: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val author: GitHubAuthor? = null,
)

@Serializable
data class GitHubAuthor(val login: String = "")
