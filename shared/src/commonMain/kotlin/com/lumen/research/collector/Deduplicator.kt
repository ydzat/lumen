package com.lumen.research.collector

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article

class Deduplicator(private val db: LumenDatabase) {

    fun deduplicate(articles: List<Article>): DeduplicationResult {
        val index = buildExistingIndex()
        val unique = mutableListOf<Article>()
        var duplicatesRemoved = 0

        for (article in articles) {
            val existingId = findDuplicate(article, index)
            if (existingId != null) {
                if (existingId > 0) {
                    mergeMetadata(existingId, article)
                }
                duplicatesRemoved++
            } else {
                index.addInMemory(article)
                unique.add(article)
            }
        }

        return DeduplicationResult(unique, duplicatesRemoved)
    }

    internal fun findDuplicate(article: Article, index: DeduplicationIndex): Long? {
        if (article.doi.isNotBlank()) {
            index.byDoi[article.doi]?.let { return it }
        }
        if (article.arxivId.isNotBlank()) {
            index.byArxivId[article.arxivId]?.let { return it }
        }
        if (article.url.isNotBlank()) {
            index.byUrl[article.url]?.let { return it }
        }
        val titleKey = buildTitleAuthorKey(article)
        if (titleKey != null) {
            index.byTitleAuthor[titleKey]?.let { return it }
        }
        return null
    }

    internal fun mergeMetadata(existingId: Long, incoming: Article) {
        val existing = db.articleBox.get(existingId) ?: return
        var merged = existing
        var changed = false

        if (existing.doi.isBlank() && incoming.doi.isNotBlank()) {
            merged = merged.copy(doi = incoming.doi)
            changed = true
        }
        if (existing.arxivId.isBlank() && incoming.arxivId.isNotBlank()) {
            merged = merged.copy(arxivId = incoming.arxivId)
            changed = true
        }
        if (existing.citationCount == 0 && incoming.citationCount > 0) {
            merged = merged.copy(citationCount = incoming.citationCount)
            changed = true
        }
        if (existing.influentialCitationCount == 0 && incoming.influentialCitationCount > 0) {
            merged = merged.copy(influentialCitationCount = incoming.influentialCitationCount)
            changed = true
        }
        if (existing.summary.isBlank() && incoming.summary.isNotBlank()) {
            merged = merged.copy(summary = incoming.summary)
            changed = true
        }

        if (changed) {
            db.articleBox.put(merged)
        }
    }

    private fun buildExistingIndex(): DeduplicationIndex {
        val index = DeduplicationIndex()
        val existing = db.articleBox.all
        for (article in existing) {
            index.addPersisted(article)
        }
        return index
    }

    companion object {
        private val NON_ALNUM_PATTERN = Regex("[^a-z0-9\\s]")
        private val MULTI_SPACE_PATTERN = Regex("\\s+")

        internal fun normalizeTitle(title: String): String {
            return title.lowercase().trim()
                .replace(NON_ALNUM_PATTERN, "")
                .replace(MULTI_SPACE_PATTERN, " ")
        }

        internal fun buildTitleAuthorKey(article: Article): String? {
            val normalizedTitle = normalizeTitle(article.title)
            if (normalizedTitle.isBlank()) return null
            return "$normalizedTitle|${article.author.lowercase().trim()}"
        }
    }
}

data class DeduplicationResult(
    val unique: List<Article>,
    val duplicatesRemoved: Int,
)

internal class DeduplicationIndex {
    val byDoi = mutableMapOf<String, Long>()
    val byArxivId = mutableMapOf<String, Long>()
    val byUrl = mutableMapOf<String, Long>()
    val byTitleAuthor = mutableMapOf<String, Long>()

    private var inMemoryCounter = -1L

    fun addPersisted(article: Article) {
        val id = article.id
        if (id == 0L) return
        addToMaps(article, id)
    }

    fun addInMemory(article: Article) {
        val id = if (article.id != 0L) article.id else inMemoryCounter--
        addToMaps(article, id)
    }

    private fun addToMaps(article: Article, id: Long) {
        if (article.doi.isNotBlank()) byDoi[article.doi] = id
        if (article.arxivId.isNotBlank()) byArxivId[article.arxivId] = id
        if (article.url.isNotBlank()) byUrl[article.url] = id
        val titleKey = Deduplicator.buildTitleAuthorKey(article)
        if (titleKey != null) byTitleAuthor[titleKey] = id
    }
}
