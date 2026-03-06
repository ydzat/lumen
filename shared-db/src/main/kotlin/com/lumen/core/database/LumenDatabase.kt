package com.lumen.core.database

import com.lumen.core.database.entities.MemoryEntry
import com.lumen.core.database.entities.Source
import io.objectbox.Box
import io.objectbox.BoxStore

class LumenDatabase(val store: BoxStore) {

    val sourceBox: Box<Source> get() = store.boxFor(Source::class.java)

    val memoryEntryBox: Box<MemoryEntry> get() = store.boxFor(MemoryEntry::class.java)

    fun close() {
        if (!store.isClosed) {
            store.close()
        }
    }
}
