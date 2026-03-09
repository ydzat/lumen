package com.lumen.research.collector

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.MyObjectBox
import com.lumen.core.database.entities.Source
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * End-to-end integration test for RssDataSource.
 * Tests the FULL flow: fetch RSS XML -> parse via RssParser -> processChannel -> persist to DB.
 * This catches issues that raw HTTP tests miss (e.g., parser failures, empty items, null links).
 *
 * Run manually: ./gradlew :shared:jvmTest --tests "*.RssDataSourceIntegrationTest"
 */
class RssDataSourceIntegrationTest {

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File
    private lateinit var dataSource: RssDataSource

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "objectbox-rss-integ-${System.nanoTime()}")
        tempDir.mkdirs()
        val store = MyObjectBox.builder().baseDirectory(tempDir).build()
        db = LumenDatabase(store)
        dataSource = RssDataSource(db) // uses createDefaultParser() with User-Agent
    }

    @AfterTest
    fun teardown() {
        db.close()
        tempDir.deleteRecursively()
    }

    private fun createSource(name: String, url: String): Source {
        val source = Source(name = name, url = url, type = "RSS")
        val id = db.sourceBox.put(source)
        return db.sourceBox.get(id)
    }

    private fun createContext(budget: Int = 100): FetchContext {
        return FetchContext(
            activeProjects = emptyList(),
            keywords = emptySet(),
            categories = emptySet(),
            remainingBudget = budget,
        )
    }

    // --- Test each RSS source through the full RssDataSource pipeline ---

    @Test
    fun fullPipeline_hackerNews() = runBlocking {
        val source = createSource("Hacker News", "https://hnrss.org/frontpage")
        val result = dataSource.fetch(listOf(source), createContext())
        println("[E2E Hacker News] articles=${result.articles.size}, errors=${result.errors}, failedIds=${result.failedSourceIds}")
        result.articles.take(3).forEach { println("  - [${it.url}] ${it.title}") }
        assertTrue(result.articles.isNotEmpty(), "Hacker News should produce articles via full pipeline")
        assertTrue(result.errors.isEmpty(), "Hacker News should have no errors")
    }

    @Test
    fun fullPipeline_openAiBlog() = runBlocking {
        val source = createSource("OpenAI Blog", "https://openai.com/news/rss.xml")
        val result = dataSource.fetch(listOf(source), createContext())
        println("[E2E OpenAI Blog] articles=${result.articles.size}, errors=${result.errors}, failedIds=${result.failedSourceIds}")
        result.articles.take(3).forEach { println("  - [${it.url}] ${it.title}") }
        assertTrue(result.articles.isNotEmpty(), "OpenAI Blog should produce articles via full pipeline")
    }

    @Test
    fun fullPipeline_githubBlog() = runBlocking {
        val source = createSource("GitHub Blog", "https://github.blog/feed/")
        val result = dataSource.fetch(listOf(source), createContext())
        println("[E2E GitHub Blog] articles=${result.articles.size}, errors=${result.errors}, failedIds=${result.failedSourceIds}")
        result.articles.take(3).forEach { println("  - [${it.url}] ${it.title}") }
        assertTrue(result.articles.isNotEmpty(), "GitHub Blog should produce articles via full pipeline")
    }

    @Test
    fun fullPipeline_anthropicNews() = runBlocking {
        val source = createSource("Anthropic News", "https://raw.githubusercontent.com/taobojlen/anthropic-rss-feed/main/anthropic_news_rss.xml")
        val result = dataSource.fetch(listOf(source), createContext())
        println("[E2E Anthropic News] articles=${result.articles.size}, errors=${result.errors}, failedIds=${result.failedSourceIds}")
        result.articles.take(3).forEach { println("  - [${it.url}] ${it.title}") }
        assertTrue(result.articles.isNotEmpty(), "Anthropic News should produce articles via full pipeline")
    }

    @Test
    fun fullPipeline_huggingFaceBlog() = runBlocking {
        val source = createSource("Hugging Face Blog", "https://huggingface.co/blog/feed.xml")
        val result = dataSource.fetch(listOf(source), createContext())
        println("[E2E Hugging Face Blog] articles=${result.articles.size}, errors=${result.errors}, failedIds=${result.failedSourceIds}")
        result.articles.take(3).forEach { println("  - [${it.url}] ${it.title}") }
        assertTrue(result.articles.isNotEmpty(), "Hugging Face Blog should produce articles via full pipeline")
    }

    @Test
    fun fullPipeline_googleDeepMind() = runBlocking {
        val source = createSource("Google DeepMind", "https://deepmind.google/blog/rss.xml")
        val result = dataSource.fetch(listOf(source), createContext())
        println("[E2E Google DeepMind] articles=${result.articles.size}, errors=${result.errors}, failedIds=${result.failedSourceIds}")
        result.articles.take(3).forEach { println("  - [${it.url}] ${it.title}") }
        assertTrue(result.articles.isNotEmpty(), "Google DeepMind should produce articles via full pipeline")
    }

    @Test
    fun fullPipeline_mitTechReview() = runBlocking {
        val source = createSource("MIT Tech Review", "https://www.technologyreview.com/feed/")
        val result = dataSource.fetch(listOf(source), createContext())
        println("[E2E MIT Tech Review] articles=${result.articles.size}, errors=${result.errors}, failedIds=${result.failedSourceIds}")
        result.articles.take(3).forEach { println("  - [${it.url}] ${it.title}") }
        assertTrue(result.articles.isNotEmpty(), "MIT Tech Review should produce articles via full pipeline")
    }

    @Test
    fun fullPipeline_qbitai() = runBlocking {
        val source = createSource("量子位", "https://www.qbitai.com/feed")
        val result = dataSource.fetch(listOf(source), createContext())
        println("[E2E 量子位] articles=${result.articles.size}, errors=${result.errors}, failedIds=${result.failedSourceIds}")
        result.articles.take(3).forEach { println("  - [${it.url}] ${it.title}") }
        assertTrue(result.articles.isNotEmpty(), "量子位 should produce articles via full pipeline")
    }

    // --- Combined test: all RSS sources at once (simulates real pipeline) ---

    @Test
    fun fullPipeline_allRssSources_fetchesFromAll() = runBlocking {
        val sources = listOf(
            createSource("Hacker News", "https://hnrss.org/frontpage"),
            createSource("OpenAI Blog", "https://openai.com/news/rss.xml"),
            createSource("GitHub Blog", "https://github.blog/feed/"),
            createSource("Anthropic News", "https://raw.githubusercontent.com/taobojlen/anthropic-rss-feed/main/anthropic_news_rss.xml"),
            createSource("Hugging Face Blog", "https://huggingface.co/blog/feed.xml"),
            createSource("Google DeepMind", "https://deepmind.google/blog/rss.xml"),
            createSource("MIT Tech Review", "https://www.technologyreview.com/feed/"),
            createSource("量子位", "https://www.qbitai.com/feed"),
        )

        val result = dataSource.fetch(sources, createContext(budget = 500))

        println("\n=== ALL RSS SOURCES COMBINED ===")
        println("Total articles: ${result.articles.size}")
        println("Errors: ${result.errors}")
        println("Failed source IDs: ${result.failedSourceIds}")

        // Group by sourceId to see per-source breakdown
        val bySource = result.articles.groupBy { it.sourceId }
        for (source in sources) {
            val count = bySource[source.id]?.size ?: 0
            println("  ${source.name}: $count articles")
        }

        // Also check what's in the DB
        val dbArticles = db.articleBox.all
        println("DB total articles: ${dbArticles.size}")
        val dbBySource = dbArticles.groupBy { it.sourceId }
        for (source in sources) {
            val count = dbBySource[source.id]?.size ?: 0
            println("  DB ${source.name} (id=${source.id}): $count articles")
        }

        // At least some sources should succeed
        assertTrue(result.articles.size > 10, "Combined RSS fetch should produce >10 articles, got ${result.articles.size}")
        // Check that most sources contributed
        val sourcesWithArticles = bySource.count { it.value.isNotEmpty() }
        println("Sources with articles: $sourcesWithArticles / ${sources.size}")
        assertTrue(sourcesWithArticles >= 5, "At least 5 of 8 RSS sources should produce articles, got $sourcesWithArticles")
    }
}
