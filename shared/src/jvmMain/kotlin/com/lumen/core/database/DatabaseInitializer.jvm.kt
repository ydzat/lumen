package com.lumen.core.database

import com.lumen.core.database.entities.MyObjectBox
import java.io.File

actual class PlatformDatabaseConfig(val directory: File)

actual fun createLumenDatabase(config: PlatformDatabaseConfig): LumenDatabase {
    val store = MyObjectBox.builder()
        .baseDirectory(config.directory)
        .build()
    return LumenDatabase(store)
}
