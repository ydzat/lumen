package com.lumen.server

import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.Digest
import com.lumen.server.config.ServerConfig
import com.lumen.server.config.ServerConfigStore
import com.lumen.server.notification.NtfyNotifier
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NtfyNotifierTest {

    private lateinit var tempDir: File
    private lateinit var configStore: ServerConfigStore

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "lumen-ntfy-test-${System.nanoTime()}")
        tempDir.mkdirs()
        configStore = ServerConfigStore(tempDir)
    }

    @AfterTest
    fun teardown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun notifyDigest_enabled_sendsCorrectRequest() = runBlocking {
        configStore.save(ServerConfig(
            ntfyServerUrl = "https://ntfy.example.com",
            ntfyTopic = "lumen-test",
        ))

        var requestUrl = ""
        var requestTitle = ""
        var requestTags = ""
        var requestBody = ""

        val mockEngine = MockEngine { request ->
            requestUrl = request.url.toString()
            requestTitle = request.headers["Title"] ?: ""
            requestTags = request.headers["Tags"] ?: ""
            requestBody = String(request.body.toByteArray())
            respond("ok", HttpStatusCode.OK)
        }
        val client = HttpClient(mockEngine)
        val notifier = NtfyNotifier(client, configStore)

        val digest = Digest(
            id = 1,
            date = "2026-03-07",
            title = "AI Research Digest",
            content = "Highlights...",
        )
        notifier.notifyDigest(digest)

        assertEquals("https://ntfy.example.com/lumen-test", requestUrl)
        assertEquals("AI Research Digest", requestTitle)
        assertEquals("newspaper", requestTags)
        assertEquals("New research digest for 2026-03-07", requestBody)
    }

    @Test
    fun notifyDigest_disabled_doesNotSend() = runBlocking {
        // Default config has empty ntfyServerUrl — disabled
        var requestCount = 0
        val mockEngine = MockEngine {
            requestCount++
            respond("ok", HttpStatusCode.OK)
        }
        val client = HttpClient(mockEngine)
        val notifier = NtfyNotifier(client, configStore)

        notifier.notifyDigest(Digest(title = "Test", date = "2026-03-07"))

        assertEquals(0, requestCount)
    }

    @Test
    fun notifyHighRelevanceArticles_filtersLowScore() = runBlocking {
        configStore.save(ServerConfig(
            ntfyServerUrl = "https://ntfy.example.com",
            ntfyTopic = "lumen-test",
        ))

        val titles = mutableListOf<String>()
        val mockEngine = MockEngine { request ->
            titles.add(request.headers["Title"] ?: "")
            respond("ok", HttpStatusCode.OK)
        }
        val client = HttpClient(mockEngine)
        val notifier = NtfyNotifier(client, configStore)

        val articles = listOf(
            Article(title = "High relevance", url = "http://a.com/1", aiRelevanceScore = 0.9f),
            Article(title = "Low relevance", url = "http://a.com/2", aiRelevanceScore = 0.5f),
            Article(title = "Also high", url = "http://a.com/3", aiRelevanceScore = 0.85f),
        )
        notifier.notifyHighRelevanceArticles(articles)

        assertEquals(2, titles.size)
        assertTrue(titles.contains("High relevance"))
        assertTrue(titles.contains("Also high"))
    }

    @Test
    fun notifyHighRelevanceArticles_noHighRelevance_doesNotSend() = runBlocking {
        configStore.save(ServerConfig(
            ntfyServerUrl = "https://ntfy.example.com",
            ntfyTopic = "lumen-test",
        ))

        var requestCount = 0
        val mockEngine = MockEngine {
            requestCount++
            respond("ok", HttpStatusCode.OK)
        }
        val client = HttpClient(mockEngine)
        val notifier = NtfyNotifier(client, configStore)

        val articles = listOf(
            Article(title = "Low", aiRelevanceScore = 0.3f),
            Article(title = "Medium", aiRelevanceScore = 0.7f),
        )
        notifier.notifyHighRelevanceArticles(articles)

        assertEquals(0, requestCount)
    }

    @Test
    fun send_serverUnreachable_doesNotThrow() = runBlocking {
        configStore.save(ServerConfig(
            ntfyServerUrl = "https://ntfy.example.com",
            ntfyTopic = "lumen-test",
        ))

        val mockEngine = MockEngine {
            throw RuntimeException("Connection refused")
        }
        val client = HttpClient(mockEngine)
        val notifier = NtfyNotifier(client, configStore)

        // Should not throw — graceful degradation
        notifier.notifyDigest(Digest(title = "Test", date = "2026-03-07"))
    }
}
