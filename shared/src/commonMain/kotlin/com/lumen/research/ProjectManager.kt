package com.lumen.research

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.ResearchProject
import com.lumen.core.memory.EmbeddingClient

class ProjectManager(
    private val db: LumenDatabase,
    private val embeddingClient: EmbeddingClient,
) {

    suspend fun create(name: String, description: String = "", keywords: String = ""): ResearchProject {
        val now = System.currentTimeMillis()
        val embedding = computeEmbedding(name, description, keywords)
        val project = ResearchProject(
            name = name,
            description = description,
            keywords = keywords,
            embedding = embedding,
            createdAt = now,
            updatedAt = now,
        )
        val id = db.researchProjectBox.put(project)
        return db.researchProjectBox.get(id)
    }

    fun get(id: Long): ResearchProject? {
        return db.researchProjectBox.get(id)
    }

    suspend fun update(project: ResearchProject): ResearchProject {
        require(project.id != 0L) { "Cannot update a project without an id" }
        val existing = db.researchProjectBox.get(project.id)
        val needsReembedding = existing == null ||
            existing.name != project.name ||
            existing.description != project.description ||
            existing.keywords != project.keywords

        val updated = if (needsReembedding) {
            val embedding = computeEmbedding(project.name, project.description, project.keywords)
            project.copy(embedding = embedding, updatedAt = System.currentTimeMillis())
        } else {
            project.copy(updatedAt = System.currentTimeMillis())
        }

        db.researchProjectBox.put(updated)
        return db.researchProjectBox.get(project.id)
    }

    fun delete(id: Long) {
        val articles = db.articleBox.all.filter { id.toString() in parseProjectIds(it.projectIds) }
        for (article in articles) {
            val updatedIds = parseProjectIds(article.projectIds) - id.toString()
            db.articleBox.put(article.copy(projectIds = updatedIds.joinToString(",")))
        }
        db.researchProjectBox.remove(id)
    }

    fun listAll(): List<ResearchProject> {
        return db.researchProjectBox.all
    }

    fun setActive(id: Long) {
        val all = db.researchProjectBox.all
        val updated = all.map { it.copy(isActive = it.id == id) }
        db.researchProjectBox.put(updated)
    }

    fun getActive(): ResearchProject? {
        return db.researchProjectBox.all.firstOrNull { it.isActive }
    }

    fun assignArticle(articleId: Long, projectId: Long) {
        val article = db.articleBox.get(articleId) ?: return
        val ids = parseProjectIds(article.projectIds).toMutableSet()
        if (ids.add(projectId.toString())) {
            db.articleBox.put(article.copy(projectIds = ids.joinToString(",")))
        }
    }

    fun unassignArticle(articleId: Long, projectId: Long) {
        val article = db.articleBox.get(articleId) ?: return
        val ids = parseProjectIds(article.projectIds).toMutableSet()
        if (ids.remove(projectId.toString())) {
            db.articleBox.put(article.copy(projectIds = ids.joinToString(",")))
        }
    }

    fun getArticlesForProject(projectId: Long): List<Article> {
        return db.articleBox.all.filter { projectId.toString() in parseProjectIds(it.projectIds) }
    }

    private suspend fun computeEmbedding(name: String, description: String, keywords: String): FloatArray {
        val text = listOf(name, description, keywords).filter { it.isNotBlank() }.joinToString(" ")
        return embeddingClient.embed(text)
    }

    private fun parseProjectIds(csv: String): Set<String> {
        if (csv.isBlank()) return emptySet()
        return csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }
}
