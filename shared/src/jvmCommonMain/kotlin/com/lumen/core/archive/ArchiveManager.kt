package com.lumen.core.archive

import com.lumen.core.config.AppConfig
import com.lumen.core.config.ConfigStore
import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article_
import com.lumen.core.database.entities.Digest_
import com.lumen.core.database.entities.Persona_
import com.lumen.core.database.entities.ResearchProject_
import com.lumen.core.database.entities.Source_
import io.objectbox.query.QueryBuilder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ArchiveManager(
    private val database: LumenDatabase,
    private val configStore: ConfigStore,
) {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun export(outputStream: OutputStream) {
        val sources = database.sourceBox.all
        val articles = database.articleBox.all
        val digests = database.digestBox.all
        val projects = database.researchProjectBox.all
        val conversations = database.conversationBox.all
        val messages = database.messageBox.all
        val personas = database.personaBox.all
        val documents = database.documentBox.all
        val documentChunks = database.documentChunkBox.all
        val memoryEntries = database.memoryEntryBox.all

        val manifest = ArchiveManifest(
            createdAt = System.currentTimeMillis(),
            counts = mapOf(
                "sources" to sources.size,
                "articles" to articles.size,
                "digests" to digests.size,
                "projects" to projects.size,
                "conversations" to conversations.size,
                "messages" to messages.size,
                "personas" to personas.size,
                "documents" to documents.size,
                "document_chunks" to documentChunks.size,
                "memory_entries" to memoryEntries.size,
            ),
        )

        val config = configStore.load()
        val maskedConfig = config.copy(llm = config.llm.copy(apiKey = MASKED_API_KEY))

        ZipOutputStream(outputStream).use { zip ->
            zip.writeJsonEntry("manifest.json", json.encodeToString(manifest))
            zip.writeJsonEntry("config.json", json.encodeToString(maskedConfig))
            zip.writeJsonEntry("sources.json", json.encodeToString(sources.map { it.toDto() }))
            zip.writeJsonEntry("articles.json", json.encodeToString(articles.map { it.toDto() }))
            zip.writeJsonEntry("digests.json", json.encodeToString(digests.map { it.toDto() }))
            zip.writeJsonEntry("projects.json", json.encodeToString(projects.map { it.toDto() }))
            zip.writeJsonEntry("conversations.json", json.encodeToString(conversations.map { it.toDto() }))
            zip.writeJsonEntry("messages.json", json.encodeToString(messages.map { it.toDto() }))
            zip.writeJsonEntry("personas.json", json.encodeToString(personas.map { it.toDto() }))
            zip.writeJsonEntry("documents.json", json.encodeToString(documents.map { it.toDto() }))
            zip.writeJsonEntry("document_chunks.json", json.encodeToString(documentChunks.map { it.toDto() }))
            zip.writeJsonEntry("memory_entries.json", json.encodeToString(memoryEntries.map { it.toDto() }))
        }
    }

    fun import(inputStream: InputStream) {
        val entries = readZipEntries(inputStream)

        val manifestJson = entries["manifest.json"]
            ?: throw IllegalArgumentException("Archive missing manifest.json")
        val manifest = json.decodeFromString<ArchiveManifest>(manifestJson)
        require(manifest.version == 1) {
            "Unsupported archive version: ${manifest.version} (expected 1)"
        }

        importConfig(entries["config.json"])

        val sourceIdMap = importSources(entries["sources.json"])
        val projectIdMap = importProjects(entries["projects.json"])
        val personaIdMap = importPersonas(entries["personas.json"])
        importArticles(entries["articles.json"], sourceIdMap, projectIdMap)
        importDigests(entries["digests.json"], projectIdMap)
        val conversationIdMap = importConversations(entries["conversations.json"], personaIdMap, projectIdMap)
        importMessages(entries["messages.json"], conversationIdMap)
        val documentIdMap = importDocuments(entries["documents.json"], projectIdMap)
        importDocumentChunks(entries["document_chunks.json"], documentIdMap, projectIdMap)
        importMemoryEntries(entries["memory_entries.json"])
    }

    private fun importConfig(configJson: String?) {
        if (configJson == null) return
        val archived = json.decodeFromString<AppConfig>(configJson)
        val current = configStore.load()
        val apiKey = if (archived.llm.apiKey == MASKED_API_KEY) current.llm.apiKey else archived.llm.apiKey
        configStore.save(archived.copy(llm = archived.llm.copy(apiKey = apiKey)))
    }

    private fun importSources(sourcesJson: String?): Map<Long, Long> {
        if (sourcesJson == null) return emptyMap()
        val dtos = json.decodeFromString<List<SourceDto>>(sourcesJson)
        val idMap = mutableMapOf<Long, Long>()
        for (dto in dtos) {
            val existing = database.sourceBox.query()
                .equal(Source_.url, dto.url, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build().use { it.findFirst() }
            if (existing != null) {
                idMap[dto.id] = existing.id
            } else {
                val entity = dto.toEntity()
                val newId = database.sourceBox.put(entity)
                idMap[dto.id] = newId
            }
        }
        return idMap
    }

    private fun importProjects(projectsJson: String?): Map<Long, Long> {
        if (projectsJson == null) return emptyMap()
        val dtos = json.decodeFromString<List<ResearchProjectDto>>(projectsJson)
        val idMap = mutableMapOf<Long, Long>()
        for (dto in dtos) {
            val existing = database.researchProjectBox.query()
                .equal(ResearchProject_.name, dto.name, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build().use { it.findFirst() }
            if (existing != null) {
                idMap[dto.id] = existing.id
            } else {
                val entity = dto.toEntity()
                val newId = database.researchProjectBox.put(entity)
                idMap[dto.id] = newId
            }
        }
        return idMap
    }

    private fun importPersonas(personasJson: String?): Map<Long, Long> {
        if (personasJson == null) return emptyMap()
        val dtos = json.decodeFromString<List<PersonaDto>>(personasJson)
        val idMap = mutableMapOf<Long, Long>()
        for (dto in dtos) {
            val existing = database.personaBox.query()
                .equal(Persona_.name, dto.name, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build().use { it.findFirst() }
            if (existing != null) {
                idMap[dto.id] = existing.id
            } else {
                val entity = dto.toEntity()
                val newId = database.personaBox.put(entity)
                idMap[dto.id] = newId
            }
        }
        return idMap
    }

    private fun importArticles(
        articlesJson: String?,
        sourceIdMap: Map<Long, Long>,
        projectIdMap: Map<Long, Long>,
    ) {
        if (articlesJson == null) return
        val dtos = json.decodeFromString<List<ArticleDto>>(articlesJson)
        for (dto in dtos) {
            val existing = database.articleBox.query()
                .equal(Article_.url, dto.url, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build().use { it.findFirst() }
            if (existing != null) continue

            val newSourceId = sourceIdMap[dto.sourceId] ?: dto.sourceId
            val newProjectIds = remapProjectIds(dto.projectIds, projectIdMap)
            database.articleBox.put(dto.toEntity(sourceId = newSourceId, projectIds = newProjectIds))
        }
    }

    private fun importDigests(digestsJson: String?, projectIdMap: Map<Long, Long>) {
        if (digestsJson == null) return
        val dtos = json.decodeFromString<List<DigestDto>>(digestsJson)
        for (dto in dtos) {
            val newProjectId = projectIdMap[dto.projectId] ?: dto.projectId
            val existing = database.digestBox.query()
                .equal(Digest_.date, dto.date, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .equal(Digest_.projectId, newProjectId)
                .build().use { it.findFirst() }
            if (existing != null) continue

            database.digestBox.put(dto.toEntity(projectId = newProjectId))
        }
    }

    private fun importConversations(
        conversationsJson: String?,
        personaIdMap: Map<Long, Long>,
        projectIdMap: Map<Long, Long>,
    ): Map<Long, Long> {
        if (conversationsJson == null) return emptyMap()
        val dtos = json.decodeFromString<List<ConversationDto>>(conversationsJson)
        val idMap = mutableMapOf<Long, Long>()
        for (dto in dtos) {
            val newPersonaId = personaIdMap[dto.personaId] ?: dto.personaId
            val newProjectId = projectIdMap[dto.projectId] ?: dto.projectId
            val entity = dto.toEntity(personaId = newPersonaId, projectId = newProjectId)
            val newId = database.conversationBox.put(entity)
            idMap[dto.id] = newId
        }
        return idMap
    }

    private fun importMessages(messagesJson: String?, conversationIdMap: Map<Long, Long>) {
        if (messagesJson == null) return
        val dtos = json.decodeFromString<List<MessageDto>>(messagesJson)
        for (dto in dtos) {
            val newConvId = conversationIdMap[dto.conversationId] ?: dto.conversationId
            database.messageBox.put(dto.toEntity(conversationId = newConvId))
        }
    }

    private fun importDocuments(documentsJson: String?, projectIdMap: Map<Long, Long>): Map<Long, Long> {
        if (documentsJson == null) return emptyMap()
        val dtos = json.decodeFromString<List<DocumentDto>>(documentsJson)
        val idMap = mutableMapOf<Long, Long>()
        for (dto in dtos) {
            val newProjectId = projectIdMap[dto.projectId] ?: dto.projectId
            val entity = dto.toEntity(projectId = newProjectId)
            val newId = database.documentBox.put(entity)
            idMap[dto.id] = newId
        }
        return idMap
    }

    private fun importDocumentChunks(
        chunksJson: String?,
        documentIdMap: Map<Long, Long>,
        projectIdMap: Map<Long, Long>,
    ) {
        if (chunksJson == null) return
        val dtos = json.decodeFromString<List<DocumentChunkDto>>(chunksJson)
        for (dto in dtos) {
            val newDocId = documentIdMap[dto.documentId] ?: dto.documentId
            val newProjectId = projectIdMap[dto.projectId] ?: dto.projectId
            database.documentChunkBox.put(dto.toEntity(documentId = newDocId, projectId = newProjectId))
        }
    }

    private fun importMemoryEntries(entriesJson: String?) {
        if (entriesJson == null) return
        val dtos = json.decodeFromString<List<MemoryEntryDto>>(entriesJson)
        for (dto in dtos) {
            database.memoryEntryBox.put(dto.toEntity())
        }
    }

    private fun remapProjectIds(projectIds: String, projectIdMap: Map<Long, Long>): String {
        if (projectIds.isBlank()) return projectIds
        return projectIds.split(",")
            .mapNotNull { it.trim().toLongOrNull() }
            .map { oldId -> projectIdMap[oldId] ?: oldId }
            .joinToString(",")
    }

    private fun readZipEntries(inputStream: InputStream): Map<String, String> {
        val entries = mutableMapOf<String, String>()
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name] = zip.bufferedReader().readText()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return entries
    }

    private fun ZipOutputStream.writeJsonEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    companion object {
        const val MASKED_API_KEY = "***"
    }
}
