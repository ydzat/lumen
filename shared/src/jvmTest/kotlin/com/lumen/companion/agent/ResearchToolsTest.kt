package com.lumen.companion.agent

import com.lumen.companion.agent.tools.GetDigestArgs
import com.lumen.companion.agent.tools.GetDigestTool
import com.lumen.companion.agent.tools.GetTrendsArgs
import com.lumen.companion.agent.tools.GetTrendsTool
import com.lumen.companion.agent.tools.SearchArticlesArgs
import com.lumen.companion.agent.tools.SearchArticlesTool
import com.lumen.core.config.LlmConfig
import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.Digest
import com.lumen.core.database.entities.EMBEDDING_DIMENSIONS
import com.lumen.core.database.entities.MyObjectBox
import com.lumen.core.memory.EmbeddingClient
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class ResearchToolsTest {

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File

    private val fakeEmbeddingClient = object : EmbeddingClient {
        override suspend fun embed(text: String): FloatArray {
            val seed = text.hashCode()
            return FloatArray(EMBEDDING_DIMENSIONS) { i -> ((seed + i) % 100) / 100f }
        }
        override suspend fun embedBatch(texts: List<String>): List<FloatArray> =
            texts.map { embed(it) }
    }

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "objectbox-test-${System.nanoTime()}")
        tempDir.mkdirs()
        val store = MyObjectBox.builder()
            .baseDirectory(tempDir)
            .build()
        db = LumenDatabase(store)
    }

    @AfterTest
    fun teardown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun searchArticlesTool_execute_returnsFormattedResults() = runBlocking {
        val embedding = fakeEmbeddingClient.embed("machine learning")
        db.articleBox.put(
            Article(
                title = "Deep Learning Advances",
                aiSummary = "A review of recent advances.",
                keywords = "deep learning,neural networks",
                url = "https://example.com/article1",
                embedding = embedding,
            ),
        )

        val tool = SearchArticlesTool(db, fakeEmbeddingClient)
        val result = tool.execute(SearchArticlesArgs(query = "machine learning", limit = 5))

        assertTrue(result.contains("Deep Learning Advances"))
        assertTrue(result.contains("A review of recent advances."))
        assertTrue(result.contains("https://example.com/article1"))
    }

    @Test
    fun searchArticlesTool_execute_emptyDb_returnsNoArticles() = runBlocking {
        val tool = SearchArticlesTool(db, fakeEmbeddingClient)
        val result = tool.execute(SearchArticlesArgs(query = "anything", limit = 5))

        assertEquals("No articles found.", result)
    }

    @Test
    fun searchArticlesTool_execute_includesDateAndRelevance() = runBlocking {
        val now = System.currentTimeMillis()
        val embedding = fakeEmbeddingClient.embed("transformers")
        db.articleBox.put(
            Article(
                title = "Attention Is All You Need",
                url = "https://example.com/attention",
                embedding = embedding,
                publishedAt = now - 86_400_000L,
                aiRelevanceScore = 0.95f,
            ),
        )

        val tool = SearchArticlesTool(db, fakeEmbeddingClient)
        val result = tool.execute(SearchArticlesArgs(query = "transformers"))

        assertTrue(result.contains("Attention Is All You Need"))
        assertTrue(result.contains("Date:"))
        assertTrue(result.contains("Relevance: 0.95"))
    }

    @Test
    fun searchArticlesTool_daysBack_filtersOldArticles() = runBlocking {
        val now = System.currentTimeMillis()
        val embedding = fakeEmbeddingClient.embed("test query")
        db.articleBox.put(
            Article(
                title = "Recent Article",
                url = "url1",
                embedding = embedding,
                publishedAt = now - 86_400_000L, // 1 day ago
            ),
        )
        db.articleBox.put(
            Article(
                title = "Old Article",
                url = "url2",
                embedding = embedding,
                publishedAt = now - 30L * 86_400_000L, // 30 days ago
            ),
        )

        val tool = SearchArticlesTool(db, fakeEmbeddingClient)
        val result = tool.execute(SearchArticlesArgs(query = "test query", daysBack = 7))

        assertTrue(result.contains("Recent Article"), "Should include recent article, got: $result")
        assertFalse(result.contains("Old Article"), "Should exclude old article, got: $result")
    }

    @Test
    fun searchArticlesTool_daysBack_zero_noDateFilter() = runBlocking {
        val now = System.currentTimeMillis()
        val embedding = fakeEmbeddingClient.embed("test query")
        db.articleBox.put(
            Article(title = "Recent", url = "url1", embedding = embedding, publishedAt = now),
        )
        db.articleBox.put(
            Article(title = "Ancient", url = "url2", embedding = embedding, publishedAt = 1000L),
        )

        val tool = SearchArticlesTool(db, fakeEmbeddingClient)
        val result = tool.execute(SearchArticlesArgs(query = "test query", daysBack = 0))

        assertTrue(result.contains("Recent"))
        assertTrue(result.contains("Ancient"))
    }

    @Test
    fun searchArticlesTool_daysBack_usesFetchedAtFallback() = runBlocking {
        val now = System.currentTimeMillis()
        val embedding = fakeEmbeddingClient.embed("test query")
        db.articleBox.put(
            Article(
                title = "No Publish Date",
                url = "url1",
                embedding = embedding,
                publishedAt = 0,
                fetchedAt = now - 86_400_000L, // fetched 1 day ago
            ),
        )

        val tool = SearchArticlesTool(db, fakeEmbeddingClient)
        val result = tool.execute(SearchArticlesArgs(query = "test query", daysBack = 7))

        assertTrue(result.contains("No Publish Date"), "Should include article with recent fetchedAt, got: $result")
    }

    @Test
    fun getDigestTool_execute_returnsDigestForDate() = runBlocking {
        db.digestBox.put(
            Digest(
                date = "2026-03-07",
                title = "AI Research Digest",
                content = "Today's highlights include...",
                createdAt = System.currentTimeMillis(),
            ),
        )

        val tool = GetDigestTool(db)
        val result = tool.execute(GetDigestArgs(date = "2026-03-07"))

        assertTrue(result.contains("AI Research Digest"))
        assertTrue(result.contains("Today's highlights include..."))
    }

    @Test
    fun getDigestTool_execute_noDigest_returnsNotFound() = runBlocking {
        val tool = GetDigestTool(db)
        val result = tool.execute(GetDigestArgs(date = "2026-01-01"))

        assertTrue(result.contains("No digest found for 2026-01-01"))
    }

    @Test
    fun getDigestTool_execute_blankDate_defaultsToToday() = runBlocking {
        val tool = GetDigestTool(db)
        val result = tool.execute(GetDigestArgs(date = ""))

        assertTrue(result.contains("No digest found for"))
    }

    @Test
    fun getTrendsTool_execute_returnsRecentDigests() = runBlocking {
        val now = System.currentTimeMillis()
        db.digestBox.put(
            Digest(
                date = "2026-03-07",
                title = "Day 1 Digest",
                content = "Trends in NLP",
                createdAt = now,
            ),
        )
        db.digestBox.put(
            Digest(
                date = "2026-03-06",
                title = "Day 2 Digest",
                content = "Trends in CV",
                createdAt = now - 86_400_000L,
            ),
        )

        val tool = GetTrendsTool(db)
        val result = tool.execute(GetTrendsArgs(days = 7))

        assertTrue(result.contains("Day 1 Digest"))
        assertTrue(result.contains("Day 2 Digest"))
        assertTrue(result.contains("2 digest(s)"))
    }

    @Test
    fun getTrendsTool_execute_noDigests_returnsNotFound() = runBlocking {
        val tool = GetTrendsTool(db)
        val result = tool.execute(GetTrendsArgs(days = 7))

        assertTrue(result.contains("No digests found"))
    }

    @Test
    fun lumenAgent_withResearchDeps_hasResearchTools() {
        val agent = LumenAgent(
            config = LlmConfig(apiKey = "test"),
            db = db,
            embeddingClient = fakeEmbeddingClient,
        )
        try {
            assertTrue(agent.tools.any { it.name == "search_articles" })
            assertTrue(agent.tools.any { it.name == "get_digest" })
            assertTrue(agent.tools.any { it.name == "get_trends" })
        } finally {
            agent.close()
        }
    }

    @Test
    fun lumenAgent_withDbOnly_hasDigestAndTrendsButNotSearch() {
        val agent = LumenAgent(
            config = LlmConfig(apiKey = "test"),
            db = db,
        )
        try {
            assertTrue(agent.tools.none { it.name == "search_articles" })
            assertTrue(agent.tools.any { it.name == "get_digest" })
            assertTrue(agent.tools.any { it.name == "get_trends" })
        } finally {
            agent.close()
        }
    }

    @Test
    fun lumenAgent_withNoOptionalDeps_hasNoTools() {
        val agent = LumenAgent(config = LlmConfig(apiKey = "test"))
        try {
            assertTrue(agent.tools.isEmpty())
        } finally {
            agent.close()
        }
    }
}
