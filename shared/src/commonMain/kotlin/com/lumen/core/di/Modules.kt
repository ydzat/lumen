package com.lumen.core.di

import com.lumen.companion.agent.LumenAgent
import com.lumen.core.config.ConfigStore
import com.lumen.companion.agent.LlmClientFactory
import com.lumen.core.memory.EmbeddingClient
import com.lumen.core.memory.KoogLlmCall
import com.lumen.core.memory.LlmCall
import com.lumen.core.memory.MemoryManager
import com.lumen.core.memory.RemoteEmbeddingClient
import com.lumen.core.memory.SemanticCompressor
import io.ktor.client.HttpClient
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
    factory<LlmCall> {
        val config = get<ConfigStore>().load().llm
        val httpClient = HttpClient()
        val client = LlmClientFactory.createClient(config, httpClient)
        val model = LlmClientFactory.resolveModel(config)
        KoogLlmCall(client, model)
    }
    single { SemanticCompressor(get()) }
    single { MemoryManager(get(), get(), get()) }
}

val researchModule = module {
    // placeholder for future research services
}

expect val platformModule: Module
