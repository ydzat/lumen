package com.lumen.core.database

import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.Digest
import com.lumen.core.database.entities.MemoryEntry
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

    fun close() {
        if (!store.isClosed) {
            store.close()
        }
    }
}
