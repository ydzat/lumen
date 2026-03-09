package com.lumen.research.collector

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import com.prof18.rssparser.RssParser
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Integration tests that actually fetch from each configured feed.
 * These tests require network access and may fail if feeds are unreachable.
 * Excluded from CI via Gradle filter.
 * Run manually to diagnose feed issues: ./gradlew :shared:jvmTest --tests "*.FeedFetchIntegrationTest"
 */
class FeedFetchIntegrationTest {

    private val httpClient = HttpClient(CIO) {
        engine {
            requestTimeout = 15000
        }
    }

    private val rssParser = RssParser()

    // --- arXiv API tests ---

    @Test
    fun arxiv_csAI_returnsValidAtomFeed() = runBlocking {
        val url = "https://export.arxiv.org/api/query?search_query=cat:cs.AI&sortBy=submittedDate&sortOrder=descending&max_results=3"
        val response = httpClient.get(url)
        println("[arXiv CS.AI] HTTP ${response.status.value}")
        val body = response.bodyAsText()
        println("[arXiv CS.AI] Response length: ${body.length}, contains <entry>: ${body.contains("<entry>")}")
        assertTrue(response.status.value in 200..299, "arXiv CS.AI returned HTTP ${response.status.value}")
        assertTrue(body.contains("<entry>"), "arXiv CS.AI response should contain entries")
    }

    @Test
    fun arxiv_withKeywords_returnsValidFeed() = runBlocking {
        // Test with multi-word keywords escaped properly
        val url = "https://export.arxiv.org/api/query?search_query=(cat:cs.AI)+AND+(all:agent+OR+all:context_engine+OR+all:memory)&max_results=3&sortBy=submittedDate&sortOrder=descending"
        val response = httpClient.get(url)
        println("[arXiv+keywords] HTTP ${response.status.value}")
        val body = response.bodyAsText()
        println("[arXiv+keywords] Response length: ${body.length}")
        assertTrue(response.status.value in 200..299, "arXiv with keywords returned HTTP ${response.status.value}")
    }

    @Test
    fun arxiv_withBadKeywords_returns400() = runBlocking {
        // Demonstrate that spaces in keywords break the API
        val url = "https://export.arxiv.org/api/query?search_query=all:context engine&max_results=1"
        val response = httpClient.get(url)
        println("[arXiv BAD] HTTP ${response.status.value}")
        // This should demonstrate the 400 error
        println("[arXiv BAD] Status: ${response.status.value} (expected 400 due to unescaped space)")
    }

    // --- RSS feed tests ---

    @Test
    fun rss_hackerNews_fetchesArticles() = runBlocking {
        val channel = rssParser.getRssChannel("https://hnrss.org/frontpage")
        println("[Hacker News] Items: ${channel.items.size}")
        assertTrue(channel.items.isNotEmpty(), "Hacker News should return items")
    }

    @Test
    fun rss_openAiBlog_fetchesArticles() = runBlocking {
        val channel = rssParser.getRssChannel("https://openai.com/news/rss.xml")
        println("[OpenAI Blog] Items: ${channel.items.size}")
        assertTrue(channel.items.isNotEmpty(), "OpenAI Blog should return items")
    }

    @Test
    fun rss_githubBlog_fetchesArticles() = runBlocking {
        try {
            val channel = rssParser.getRssChannel("https://github.blog/feed/")
            println("[GitHub Blog] Items: ${channel.items.size}")
            assertTrue(channel.items.isNotEmpty(), "GitHub Blog should return items")
        } catch (e: Exception) {
            println("[GitHub Blog] FAILED: ${e::class.simpleName}: ${e.message}")
            throw e
        }
    }

    @Test
    fun rss_anthropicNews_fetchesArticles() = runBlocking {
        try {
            val channel = rssParser.getRssChannel(
                "https://raw.githubusercontent.com/taobojlen/anthropic-rss-feed/main/anthropic_news_rss.xml"
            )
            println("[Anthropic News] Items: ${channel.items.size}")
            assertTrue(channel.items.isNotEmpty(), "Anthropic News should return items")
        } catch (e: Exception) {
            println("[Anthropic News] FAILED: ${e::class.simpleName}: ${e.message}")
            throw e
        }
    }

    @Test
    fun rss_huggingFaceBlog_fetchesArticles() = runBlocking {
        try {
            val channel = rssParser.getRssChannel("https://huggingface.co/blog/feed.xml")
            println("[Hugging Face Blog] Items: ${channel.items.size}")
            assertTrue(channel.items.isNotEmpty(), "Hugging Face Blog should return items")
        } catch (e: Exception) {
            println("[Hugging Face Blog] FAILED: ${e::class.simpleName}: ${e.message}")
            throw e
        }
    }

    @Test
    fun rss_googleDeepMind_fetchesArticles() = runBlocking {
        try {
            val channel = rssParser.getRssChannel("https://deepmind.google/blog/rss.xml")
            println("[Google DeepMind] Items: ${channel.items.size}")
            assertTrue(channel.items.isNotEmpty(), "Google DeepMind should return items")
        } catch (e: Exception) {
            println("[Google DeepMind] FAILED: ${e::class.simpleName}: ${e.message}")
            throw e
        }
    }

    @Test
    fun rss_mitTechReview_fetchesArticles() = runBlocking {
        try {
            val channel = rssParser.getRssChannel("https://www.technologyreview.com/feed/")
            println("[MIT Tech Review] Items: ${channel.items.size}")
            assertTrue(channel.items.isNotEmpty(), "MIT Tech Review should return items")
        } catch (e: Exception) {
            println("[MIT Tech Review] FAILED: ${e::class.simpleName}: ${e.message}")
            throw e
        }
    }

    @Test
    fun rss_qbitai_requiresUserAgent() = runBlocking {
        // 量子位 returns 403 without User-Agent, works with it
        val parserWithUa = RssDataSource.createDefaultParser()
        val channel = parserWithUa.getRssChannel("https://www.qbitai.com/feed")
        println("[量子位] Items: ${channel.items.size}")
        assertTrue(channel.items.isNotEmpty(), "量子位 with User-Agent should return items")
    }

    // --- Semantic Scholar test ---

    @Test
    fun semanticScholar_searchReturnsResults() = runBlocking {
        val url = "https://api.semanticscholar.org/graph/v1/paper/search?query=agent+memory&offset=0&limit=3&fields=title,abstract,authors,url"
        val response = httpClient.get(url)
        println("[Semantic Scholar] HTTP ${response.status.value}")
        val body = response.bodyAsText()
        println("[Semantic Scholar] Response length: ${body.length}")
        // 429 = rate limited (temporary, has retry logic in ScholarDataSource)
        assertTrue(response.status.value in listOf(200, 429), "Semantic Scholar returned HTTP ${response.status.value}")
    }
}
