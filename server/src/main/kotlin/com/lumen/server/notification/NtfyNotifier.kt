package com.lumen.server.notification

import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.Digest
import com.lumen.server.config.ServerConfig
import com.lumen.server.config.ServerConfigStore
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import org.slf4j.LoggerFactory

class NtfyNotifier(
    private val httpClient: HttpClient,
    private val configStore: ServerConfigStore,
) {

    private val logger = LoggerFactory.getLogger(NtfyNotifier::class.java)

    suspend fun notifyDigest(digest: Digest) {
        val config = configStore.load()
        if (!isEnabled(config)) return
        send(
            title = digest.title,
            message = "New research digest for ${digest.date}",
            tags = "newspaper",
            config = config,
        )
    }

    suspend fun notifyHighRelevanceArticles(articles: List<Article>) {
        val config = configStore.load()
        if (!isEnabled(config)) return
        val highRelevance = articles.filter { it.aiRelevanceScore > HIGH_RELEVANCE_THRESHOLD }
        for (article in highRelevance) {
            send(
                title = article.title,
                message = article.url,
                tags = "star",
                config = config,
            )
        }
    }

    private fun isEnabled(config: ServerConfig): Boolean =
        config.ntfyServerUrl.isNotBlank() && config.ntfyTopic.isNotBlank()

    private suspend fun send(title: String, message: String, tags: String, config: ServerConfig) {
        try {
            val url = "${config.ntfyServerUrl.trimEnd('/')}/${config.ntfyTopic}"
            httpClient.post(url) {
                header("Title", title)
                header("Tags", tags)
                setBody(message)
            }
        } catch (e: Exception) {
            logger.warn("ntfy notification failed: ${e.message}")
        }
    }

    companion object {
        const val HIGH_RELEVANCE_THRESHOLD = 0.8f
    }
}
