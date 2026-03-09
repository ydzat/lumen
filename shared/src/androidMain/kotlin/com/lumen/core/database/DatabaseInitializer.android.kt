package com.lumen.core.database

import android.content.Context
import com.lumen.core.database.entities.MyObjectBox

actual class PlatformDatabaseConfig(val context: Context)

actual fun createLumenDatabase(config: PlatformDatabaseConfig): LumenDatabase {
    val store = MyObjectBox.builder()
        .androidContext(config.context)
        .build()
    return LumenDatabase(store)
}
