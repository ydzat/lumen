package com.lumen.research.collector

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Source

class SourceManager(private val db: LumenDatabase) {

    fun add(source: Source): Source {
        val id = db.sourceBox.put(source.copy(createdAt = System.currentTimeMillis()))
        return db.sourceBox.get(id)
    }

    fun remove(id: Long) {
        db.sourceBox.remove(id)
    }

    fun update(source: Source): Source {
        require(source.id != 0L) { "Cannot update a source without an id" }
        db.sourceBox.put(source)
        return db.sourceBox.get(source.id)
    }

    fun get(id: Long): Source? {
        return db.sourceBox.get(id)
    }

    fun listAll(): List<Source> {
        return db.sourceBox.all
    }

    fun listEnabled(): List<Source> {
        return db.sourceBox.all.filter { it.enabled }
    }

    fun toggleEnabled(id: Long): Source? {
        val source = db.sourceBox.get(id) ?: return null
        val updated = source.copy(enabled = !source.enabled)
        db.sourceBox.put(updated)
        return db.sourceBox.get(id)
    }

    fun recordFailure(id: Long, error: String): Source? {
        val source = db.sourceBox.get(id) ?: return null
        val failures = source.consecutiveFailures + 1
        val now = System.currentTimeMillis()
        val updated = source.copy(
            consecutiveFailures = failures,
            lastError = error,
            nextRetryAt = RetryPolicy.computeNextRetryAt(now, failures),
        )
        db.sourceBox.put(updated)
        return db.sourceBox.get(id)
    }

    fun recordSuccess(id: Long): Source? {
        val source = db.sourceBox.get(id) ?: return null
        val updated = source.copy(
            consecutiveFailures = 0,
            lastError = "",
            nextRetryAt = 0,
            lastFetchedAt = System.currentTimeMillis(),
        )
        db.sourceBox.put(updated)
        return db.sourceBox.get(id)
    }

    fun listRetryable(): List<Source> {
        val now = System.currentTimeMillis()
        return db.sourceBox.all.filter { it.enabled && RetryPolicy.isRetryable(it, now) }
    }

    fun seedDefaultsIfEmpty() {
        if (db.sourceBox.isEmpty) {
            val now = System.currentTimeMillis()
            db.sourceBox.put(DEFAULT_SOURCES.map { it.copy(createdAt = now) })
        }
    }

    fun seedNewDefaults() {
        val existingUrls = db.sourceBox.all.map { it.url }.toSet()
        val now = System.currentTimeMillis()
        val newSources = DEFAULT_SOURCES
            .filter { it.url !in existingUrls }
            .map { it.copy(createdAt = now) }
        if (newSources.isNotEmpty()) {
            db.sourceBox.put(newSources)
        }
    }

    fun resetAllFailures() {
        val failed = db.sourceBox.all.filter { it.consecutiveFailures > 0 }
        if (failed.isNotEmpty()) {
            db.sourceBox.put(failed.map {
                it.copy(consecutiveFailures = 0, lastError = "", nextRetryAt = 0)
            })
        }
    }

    companion object {
        val DEFAULT_SOURCES = listOf(
            Source(
                name = "arXiv CS.AI",
                url = "https://export.arxiv.org/api/query?search_query=cat:cs.AI&sortBy=submittedDate&sortOrder=descending",
                type = "ARXIV_API",
                category = "academic",
                description = "Artificial Intelligence",
            ),
            Source(
                name = "arXiv CS.LG",
                url = "https://export.arxiv.org/api/query?search_query=cat:cs.LG&sortBy=submittedDate&sortOrder=descending",
                type = "ARXIV_API",
                category = "academic",
                description = "Machine Learning",
            ),
            Source(
                name = "Semantic Scholar",
                url = "https://api.semanticscholar.org/graph/v1/paper/search",
                type = "SEMANTIC_SCHOLAR",
                category = "academic",
                description = "AI and ML research papers",
            ),
            Source(
                name = "Hacker News",
                url = "https://hnrss.org/frontpage",
                type = "RSS",
                category = "tech",
                description = "Hacker News Front Page",
            ),
            Source(
                name = "OpenAI Blog",
                url = "https://openai.com/news/rss.xml",
                type = "RSS",
                category = "tech",
                description = "OpenAI research and announcements",
            ),
            Source(
                name = "GitHub Blog",
                url = "https://github.blog/feed/",
                type = "RSS",
                category = "tech",
                description = "GitHub product announcements and features",
            ),
            Source(
                name = "Anthropic News",
                url = "https://raw.githubusercontent.com/taobojlen/anthropic-rss-feed/main/anthropic_news_rss.xml",
                type = "RSS",
                category = "tech",
                description = "Anthropic news and announcements (community-maintained)",
            ),
            Source(
                name = "Hugging Face Blog",
                url = "https://huggingface.co/blog/feed.xml",
                type = "RSS",
                category = "tech",
                description = "Open-source AI models, datasets and tools",
            ),
            Source(
                name = "Google DeepMind",
                url = "https://deepmind.google/blog/rss.xml",
                type = "RSS",
                category = "tech",
                description = "Google DeepMind research and product news",
            ),
            Source(
                name = "MIT Technology Review",
                url = "https://www.technologyreview.com/feed/",
                type = "RSS",
                category = "tech",
                description = "Technology and AI industry analysis",
            ),
            Source(
                name = "量子位",
                url = "https://www.qbitai.com/feed",
                type = "RSS",
                category = "tech",
                description = "中文AI资讯，追踪人工智能新趋势",
            ),
        )
    }
}
