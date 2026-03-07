package com.lumen.research.collector

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.MyObjectBox
import com.lumen.core.database.entities.Source
import com.lumen.core.memory.EmbeddingClient
import com.lumen.core.memory.LlmCall
import com.lumen.research.analyzer.ArticleAnalyzer
import com.lumen.research.analyzer.RelevanceScorer
import com.lumen.research.digest.DigestGenerator
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CollectorManagerTest {

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File

    private val noopLlmCall = LlmCall { _, _ -> """{"summary": "test summary", "keywords": ["ai"]}""" }

    private val fakeEmbeddingClient = object : EmbeddingClient {
        override suspend fun embed(text: String): FloatArray = FloatArray(384) { 0.1f }
        override suspend fun embedBatch(texts: List<String>): List<FloatArray> = texts.map { embed(it) }
    }

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "objectbox-test-${System.nanoTime()}")
        tempDir.mkdirs()
        val store = MyObjectBox.builder().baseDirectory(tempDir).build()
        db = LumenDatabase(store)
    }

    @AfterTest
    fun teardown() {
        db.close()
        tempDir.deleteRecursively()
    }

    private fun createManager(): CollectorManager {
        val rssCollector = RssCollector(db)
        val analyzer = ArticleAnalyzer(db, noopLlmCall, fakeEmbeddingClient)
        val scorer = RelevanceScorer(db, null)
        val digestGenerator = DigestGenerator(db, noopLlmCall, null)
        return CollectorManager(rssCollector, analyzer, scorer, digestGenerator)
    }

    @Test
    fun runPipeline_withNoSources_returnsZeroCounts() = runBlocking {
        val manager = createManager()

        val result = manager.runPipeline()

        assertEquals(0, result.fetched)
        assertEquals(0, result.analyzed)
        assertEquals(0, result.scored)
    }

    @Test
    fun runPipeline_withSourceButNoNetwork_returnsZeroFetched() = runBlocking {
        db.sourceBox.put(
            Source(name = "Unreachable", url = "https://invalid.test.example/feed", type = "rss")
        )
        val manager = createManager()

        val result = manager.runPipeline()

        assertEquals(0, result.fetched)
        assertEquals(0, result.analyzed)
        assertEquals(0, result.scored)
    }

    @Test
    fun runPipeline_digestGenerated_forToday() = runBlocking {
        val manager = createManager()

        val result = manager.runPipeline()

        assertNotNull(result.digest)
        assertEquals("No articles for ${result.digest!!.date}", result.digest!!.title)
    }

    @Test
    fun runNow_delegatesToRunPipeline() = runBlocking {
        val manager = createManager()

        val result = manager.runNow()

        assertEquals(0, result.fetched)
        assertEquals(0, result.analyzed)
        assertEquals(0, result.scored)
    }
}
