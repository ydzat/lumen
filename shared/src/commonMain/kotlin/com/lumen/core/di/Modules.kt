package com.lumen.core.di

import com.lumen.companion.agent.LumenAgent
import com.lumen.core.config.ConfigStore
import com.lumen.core.memory.EmbeddingClient
import com.lumen.core.memory.MemoryManager
import com.lumen.core.memory.RemoteEmbeddingClient
import org.koin.core.module.Module
import org.koin.dsl.module

val companionModule = module {
    factory {
        val config = get<ConfigStore>().load().llm
        LumenAgent(config)
    }
}

val memoryModule = module {
    factory<EmbeddingClient> {
        val config = get<ConfigStore>().load().llm
        RemoteEmbeddingClient(config)
    }
    single { MemoryManager(get(), get()) }
}

val researchModule = module {
    // placeholder for future research services
}

expect val platformModule: Module
