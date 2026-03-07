package com.lumen.core.di

import com.lumen.core.config.ConfigStore
import com.lumen.core.database.PlatformDatabaseConfig
import com.lumen.core.database.createLumenDatabase
import com.lumen.core.memory.EmbeddingClient
import com.lumen.core.memory.ModelResourceLoader
import com.lumen.core.memory.OnnxEmbeddingClient
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module
import java.io.File

actual val platformModule: Module = module {
    single { ConfigStore() }
    single { ModelResourceLoader() }
    single<EmbeddingClient> { OnnxEmbeddingClient(get()) }
    single {
        val dbDir = File(System.getProperty("user.home"), ".lumen/db")
        createLumenDatabase(PlatformDatabaseConfig(dbDir))
    } withOptions { onClose { it?.close() } }
}
