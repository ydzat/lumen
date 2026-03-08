package com.lumen.core.database

import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.Conversation
import com.lumen.core.database.entities.Digest
import com.lumen.core.database.entities.Document
import com.lumen.core.database.entities.ArticleSection
import com.lumen.core.database.entities.DocumentChunk
import com.lumen.core.database.entities.MemoryEntry
import com.lumen.core.database.entities.Message
import com.lumen.core.database.entities.Persona
import com.lumen.core.database.entities.ResearchProject
import com.lumen.core.database.entities.Source
import io.objectbox.Box
import io.objectbox.BoxStore

class LumenDatabase(val store: BoxStore) {

    val sourceBox: Box<Source> get() = store.boxFor(Source::class.java)

    val memoryEntryBox: Box<MemoryEntry> get() = store.boxFor(MemoryEntry::class.java)

    val articleBox: Box<Article> get() = store.boxFor(Article::class.java)

    val digestBox: Box<Digest> get() = store.boxFor(Digest::class.java)

    val researchProjectBox: Box<ResearchProject> get() = store.boxFor(ResearchProject::class.java)

    val conversationBox: Box<Conversation> get() = store.boxFor(Conversation::class.java)

    val messageBox: Box<Message> get() = store.boxFor(Message::class.java)

    val personaBox: Box<Persona> get() = store.boxFor(Persona::class.java)

    val documentBox: Box<Document> get() = store.boxFor(Document::class.java)

    val documentChunkBox: Box<DocumentChunk> get() = store.boxFor(DocumentChunk::class.java)

    val articleSectionBox: Box<ArticleSection> get() = store.boxFor(ArticleSection::class.java)

    fun close() {
        if (!store.isClosed) {
            store.close()
        }
    }
}
