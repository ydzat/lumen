package com.lumen.core.di

import com.lumen.core.config.ConfigStore
import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.PlatformDatabaseConfig
import com.lumen.core.database.createLumenDatabase
import com.lumen.core.memory.EmbeddingClient
import com.lumen.core.memory.ModelResourceLoader
import com.lumen.core.memory.OnnxEmbeddingClient
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single { ConfigStore(get()) }
    single { ModelResourceLoader(get()) }
    single<EmbeddingClient> { OnnxEmbeddingClient(get()) }
    single { createLumenDatabase(PlatformDatabaseConfig(get())) }
}
