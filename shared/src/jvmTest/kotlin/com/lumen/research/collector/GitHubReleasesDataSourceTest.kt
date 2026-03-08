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
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitHubReleasesDataSourceTest {

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "objectbox-github-test-${System.nanoTime()}")
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
    ): GitHubReleasesDataSource {
        val mockEngine = MockEngine {
            respond(
                content = responseJson,
                status = statusCode,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return GitHubReleasesDataSource(db, HttpClient(mockEngine))
    }

    private fun createContext(budget: Int = 100): FetchContext {
        return FetchContext(
            activeProjects = emptyList(),
            keywords = emptySet(),
            categories = emptySet(),
            remainingBudget = budget,
        )
    }

    private fun createSource(config: String = SAMPLE_CONFIG): Source {
        val source = Source(
            name = "GitHub Releases",
            url = "https://api.github.com",
            type = "GITHUB_RELEASES",
            config = config,
        )
        db.sourceBox.put(source)
        return db.sourceBox.all.last()
    }

    // --- Parse and Map Tests ---

    @Test
    fun parseResponse_extractsAllFields() {
        val ds = createDataSource(SAMPLE_RELEASES_JSON)
        val releases = ds.parseResponse(SAMPLE_RELEASES_JSON)

        assertEquals(2, releases.size)
        assertEquals(1L, releases[0].id)
        assertEquals("v1.0.0", releases[0].tagName)
        assertEquals("Release 1.0.0", releases[0].name)
        assertEquals("First stable release", releases[0].body)
        assertEquals("https://github.com/test/repo/releases/tag/v1.0.0", releases[0].htmlUrl)
        assertEquals("2024-01-15T10:00:00Z", releases[0].publishedAt)
        assertEquals(false, releases[0].prerelease)
        assertEquals(false, releases[0].draft)
        assertEquals("octocat", releases[0].author?.login)
    }

    @Test
    fun mapToArticle_mapsFieldsCorrectly() {
        val ds = createDataSource("[]")
        val release = GitHubRelease(
            id = 1,
            tagName = "v1.0.0",
            name = "Release 1.0.0",
            body = "First stable release with many features",
            htmlUrl = "https://github.com/test/repo/releases/tag/v1.0.0",
            publishedAt = "2024-01-15T10:00:00Z",
            author = GitHubAuthor("octocat"),
        )

        val article = ds.mapToArticle(release, "test/repo", 42L)

        assertNotNull(article)
        assertEquals("test/repo: Release 1.0.0", article.title)
        assertEquals("https://github.com/test/repo/releases/tag/v1.0.0", article.url)
        assertEquals("First stable release with many features", article.summary)
        assertEquals("First stable release with many features", article.content)
        assertEquals("octocat", article.author)
        assertEquals(42L, article.sourceId)
        assertEquals("GITHUB_RELEASES", article.sourceType)
        assertTrue(article.publishedAt > 0)
    }

    @Test
    fun mapToArticle_usesTagNameWhenNameIsNull() {
        val ds = createDataSource("[]")
        val release = GitHubRelease(
            tagName = "v2.0.0",
            name = null,
            htmlUrl = "https://github.com/test/repo/releases/tag/v2.0.0",
        )

        val article = ds.mapToArticle(release, "test/repo", 1L)

        assertNotNull(article)
        assertEquals("test/repo: v2.0.0", article.title)
    }

    @Test
    fun mapToArticle_usesTagNameWhenNameIsBlank() {
        val ds = createDataSource("[]")
        val release = GitHubRelease(
            tagName = "v2.0.0",
            name = "",
            htmlUrl = "https://github.com/test/repo/releases/tag/v2.0.0",
        )

        val article = ds.mapToArticle(release, "test/repo", 1L)

        assertNotNull(article)
        assertEquals("test/repo: v2.0.0", article.title)
    }

    @Test
    fun mapToArticle_truncatesSummary() {
        val ds = createDataSource("[]")
        val longBody = "A".repeat(1000)
        val release = GitHubRelease(
            tagName = "v1.0.0",
            body = longBody,
            htmlUrl = "https://github.com/test/repo/releases/tag/v1.0.0",
        )

        val article = ds.mapToArticle(release, "test/repo", 1L)

        assertNotNull(article)
        assertEquals(500, article.summary.length)
        assertEquals(1000, article.content.length)
    }

    @Test
    fun mapToArticle_withBlankUrl_returnsNull() {
        val ds = createDataSource("[]")
        val release = GitHubRelease(tagName = "v1.0.0", htmlUrl = "")

        val article = ds.mapToArticle(release, "test/repo", 1L)

        assertNull(article)
    }

    // --- Config Tests ---

    @Test
    fun parseConfig_validJson() {
        val config = GitHubReleasesDataSource.parseConfig(
            """{"repos":["anthropics/claude-code","NVIDIA/cuda-toolkit"],"includePrerelease":true,"maxReleasesPerRepo":5}"""
        )

        assertNotNull(config)
        assertEquals(listOf("anthropics/claude-code", "NVIDIA/cuda-toolkit"), config.repos)
        assertEquals(true, config.includePrerelease)
        assertEquals(5, config.maxReleasesPerRepo)
    }

    @Test
    fun parseConfig_blankString_returnsNull() {
        assertNull(GitHubReleasesDataSource.parseConfig(""))
    }

    @Test
    fun parseConfig_invalidJson_returnsNull() {
        assertNull(GitHubReleasesDataSource.parseConfig("not json"))
    }

    @Test
    fun parseConfig_defaults() {
        val config = GitHubReleasesDataSource.parseConfig("""{"repos":["test/repo"]}""")

        assertNotNull(config)
        assertEquals(false, config.includePrerelease)
        assertEquals(GitHubReleasesDataSource.MAX_PER_REPO_DEFAULT, config.maxReleasesPerRepo)
    }

    // --- URL Building ---

    @Test
    fun buildApiUrl_constructsCorrectUrl() {
        val ds = createDataSource("[]")
        val url = ds.buildApiUrl("anthropics/claude-code", 10)

        assertEquals("https://api.github.com/repos/anthropics/claude-code/releases?per_page=10", url)
    }

    // --- ISO 8601 Parsing ---

    @Test
    fun parseIso8601_validDate() {
        val millis = GitHubReleasesDataSource.parseIso8601("2024-01-15T10:00:00Z")
        assertTrue(millis > 0)
    }

    @Test
    fun parseIso8601_nullOrBlank_returnsZero() {
        assertEquals(0L, GitHubReleasesDataSource.parseIso8601(null))
        assertEquals(0L, GitHubReleasesDataSource.parseIso8601(""))
    }

    @Test
    fun parseIso8601_invalidFormat_returnsZero() {
        assertEquals(0L, GitHubReleasesDataSource.parseIso8601("not a date"))
    }

    // --- Fetch Tests ---

    @Test
    fun fetch_withValidResponse_returnsArticles() = runBlocking {
        val ds = createDataSource(SAMPLE_RELEASES_JSON)
        val source = createSource()

        val result = ds.fetch(listOf(source), createContext())

        assertEquals(2, result.articles.size)
        assertTrue(result.errors.isEmpty())
        assertEquals(SourceType.GITHUB_RELEASES, result.sourceType)
        assertEquals("test/repo: Release 1.0.0", result.articles[0].title)
        assertEquals(source.id, result.articles[0].sourceId)
        assertTrue(result.articles[0].fetchedAt > 0)
    }

    @Test
    fun fetch_respectsBudget() = runBlocking {
        val ds = createDataSource(SAMPLE_RELEASES_JSON)
        val source = createSource()

        val result = ds.fetch(listOf(source), createContext(budget = 1))

        assertEquals(1, result.articles.size)
    }

    @Test
    fun fetch_withHttpError_returnsError() = runBlocking {
        val ds = createDataSource("Rate limit exceeded", HttpStatusCode.Forbidden)
        val source = createSource()

        val result = ds.fetch(listOf(source), createContext())

        assertTrue(result.articles.isEmpty())
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0].contains("HTTP 403"))
    }

    @Test
    fun fetch_filtersPrerelease() = runBlocking {
        val ds = createDataSource(RELEASES_WITH_PRERELEASE_JSON)
        val source = createSource("""{"repos":["test/repo"],"includePrerelease":false}""")

        val result = ds.fetch(listOf(source), createContext())

        assertEquals(1, result.articles.size)
        assertEquals("test/repo: Stable Release", result.articles[0].title)
    }

    @Test
    fun fetch_includesPrerelease_whenConfigured() = runBlocking {
        val ds = createDataSource(RELEASES_WITH_PRERELEASE_JSON)
        val source = createSource("""{"repos":["test/repo"],"includePrerelease":true}""")

        val result = ds.fetch(listOf(source), createContext())

        assertEquals(2, result.articles.size)
    }

    @Test
    fun fetch_filtersDraftReleases() = runBlocking {
        val ds = createDataSource(RELEASES_WITH_DRAFT_JSON)
        val source = createSource()

        val result = ds.fetch(listOf(source), createContext())

        assertEquals(1, result.articles.size)
        assertEquals("test/repo: Published Release", result.articles[0].title)
    }

    @Test
    fun fetch_skipsDuplicateUrls() = runBlocking {
        db.articleBox.put(
            Article(
                title = "Existing",
                url = "https://github.com/test/repo/releases/tag/v1.0.0",
                sourceType = "GITHUB_RELEASES",
            )
        )
        val ds = createDataSource(SAMPLE_RELEASES_JSON)
        val source = createSource()

        val result = ds.fetch(listOf(source), createContext())

        assertEquals(1, result.articles.size)
        assertEquals("test/repo: Release 2.0.0", result.articles[0].title)
    }

    @Test
    fun fetch_persistsArticlesToDb() = runBlocking {
        val ds = createDataSource(SAMPLE_RELEASES_JSON)
        val source = createSource()

        ds.fetch(listOf(source), createContext())

        val stored = db.articleBox.all
        assertEquals(2, stored.size)
    }

    @Test
    fun fetch_updatesSourceLastFetchedAt() = runBlocking {
        val ds = createDataSource(SAMPLE_RELEASES_JSON)
        val source = createSource()
        assertEquals(0L, source.lastFetchedAt)

        ds.fetch(listOf(source), createContext())

        val updated = db.sourceBox.get(source.id)
        assertTrue(updated.lastFetchedAt > 0)
    }

    @Test
    fun fetch_withEmptySources_returnsEmpty() = runBlocking {
        val ds = createDataSource("[]")

        val result = ds.fetch(emptyList(), createContext())

        assertTrue(result.articles.isEmpty())
        assertTrue(result.errors.isEmpty())
        assertEquals(SourceType.GITHUB_RELEASES, result.sourceType)
    }

    @Test
    fun type_isGithubReleases() {
        val ds = createDataSource("[]")
        assertEquals(SourceType.GITHUB_RELEASES, ds.type)
    }

    @Test
    fun displayName_isGithubReleases() {
        val ds = createDataSource("[]")
        assertEquals("GitHub Releases", ds.displayName)
    }

    companion object {
        private const val SAMPLE_CONFIG = """{"repos":["test/repo"]}"""

        private val SAMPLE_RELEASES_JSON = """
            [
                {
                    "id": 1,
                    "tag_name": "v1.0.0",
                    "name": "Release 1.0.0",
                    "body": "First stable release",
                    "html_url": "https://github.com/test/repo/releases/tag/v1.0.0",
                    "published_at": "2024-01-15T10:00:00Z",
                    "prerelease": false,
                    "draft": false,
                    "author": {"login": "octocat"}
                },
                {
                    "id": 2,
                    "tag_name": "v2.0.0",
                    "name": "Release 2.0.0",
                    "body": "Second release",
                    "html_url": "https://github.com/test/repo/releases/tag/v2.0.0",
                    "published_at": "2024-06-01T12:00:00Z",
                    "prerelease": false,
                    "draft": false,
                    "author": {"login": "octocat"}
                }
            ]
        """.trimIndent()

        private val RELEASES_WITH_PRERELEASE_JSON = """
            [
                {
                    "id": 1,
                    "tag_name": "v1.0.0",
                    "name": "Stable Release",
                    "body": "Stable",
                    "html_url": "https://github.com/test/repo/releases/tag/v1.0.0",
                    "published_at": "2024-01-15T10:00:00Z",
                    "prerelease": false,
                    "draft": false,
                    "author": {"login": "octocat"}
                },
                {
                    "id": 2,
                    "tag_name": "v2.0.0-beta.1",
                    "name": "Beta Release",
                    "body": "Beta",
                    "html_url": "https://github.com/test/repo/releases/tag/v2.0.0-beta.1",
                    "published_at": "2024-06-01T12:00:00Z",
                    "prerelease": true,
                    "draft": false,
                    "author": {"login": "octocat"}
                }
            ]
        """.trimIndent()

        private val RELEASES_WITH_DRAFT_JSON = """
            [
                {
                    "id": 1,
                    "tag_name": "v1.0.0",
                    "name": "Published Release",
                    "body": "Published",
                    "html_url": "https://github.com/test/repo/releases/tag/v1.0.0",
                    "published_at": "2024-01-15T10:00:00Z",
                    "prerelease": false,
                    "draft": false,
                    "author": {"login": "octocat"}
                },
                {
                    "id": 2,
                    "tag_name": "v2.0.0",
                    "name": "Draft Release",
                    "body": "Draft",
                    "html_url": "https://github.com/test/repo/releases/tag/v2.0.0",
                    "published_at": "2024-06-01T12:00:00Z",
                    "prerelease": false,
                    "draft": true,
                    "author": {"login": "octocat"}
                }
            ]
        """.trimIndent()
    }
}
