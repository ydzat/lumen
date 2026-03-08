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

    fun seedDefaultsIfEmpty() {
        if (db.sourceBox.isEmpty) {
            val now = System.currentTimeMillis()
            db.sourceBox.put(DEFAULT_SOURCES.map { it.copy(createdAt = now) })
        }
    }

    companion object {
        val DEFAULT_SOURCES = listOf(
            Source(
                name = "arXiv CS.AI",
                url = "https://rss.arxiv.org/rss/cs.AI",
                type = "RSS",
                category = "academic",
                description = "Artificial Intelligence",
            ),
            Source(
                name = "arXiv CS.LG",
                url = "https://rss.arxiv.org/rss/cs.LG",
                type = "RSS",
                category = "academic",
                description = "Machine Learning",
            ),
            Source(
                name = "arXiv CS.CL",
                url = "https://rss.arxiv.org/rss/cs.CL",
                type = "RSS",
                category = "academic",
                description = "Computation and Language",
            ),
            Source(
                name = "arXiv CS.CV",
                url = "https://rss.arxiv.org/rss/cs.CV",
                type = "RSS",
                category = "academic",
                description = "Computer Vision and Pattern Recognition",
            ),
            Source(
                name = "Hacker News",
                url = "https://hnrss.org/frontpage",
                type = "RSS",
                category = "tech",
                description = "Hacker News Front Page",
            ),
        )
    }
}
