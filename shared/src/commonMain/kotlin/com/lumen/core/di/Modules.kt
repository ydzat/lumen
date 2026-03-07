package com.lumen.core.di

import com.lumen.companion.agent.LlmClientFactory
import com.lumen.companion.agent.LumenAgent
import com.lumen.core.config.ConfigStore
import com.lumen.core.memory.IntentRetriever
import com.lumen.core.memory.KoogLlmCall
import com.lumen.core.memory.LlmCall
import com.lumen.core.memory.MemoryManager
import com.lumen.core.memory.SemanticCompressor
import com.lumen.core.memory.SemanticSynthesizer
import com.lumen.research.ProjectManager
import com.lumen.research.analyzer.ArticleAnalyzer
import com.lumen.research.analyzer.RelevanceScorer
import com.lumen.research.collector.RssCollector
import com.lumen.research.collector.SourceManager
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

val companionModule = module {
    factory {
        val config = get<ConfigStore>().load().llm
        val memoryManager = getOrNull<MemoryManager>()
        LumenAgent(config, memoryManager)
    }
}

val memoryModule = module {
    factory<LlmCall> {
        val config = get<ConfigStore>().load().llm
        val httpClient = HttpClient()
        val client = LlmClientFactory.createClient(config, httpClient)
        val model = LlmClientFactory.resolveModel(config)
        KoogLlmCall(client, model)
    }
    single { SemanticCompressor(get()) }
    single { SemanticSynthesizer(get(), get(), get()) }
    single { IntentRetriever(get(), get(), get()) }
    single { MemoryManager(get(), get(), get(), get(), get()) }
}

val researchModule = module {
    single { RssCollector(get()) }
    single { SourceManager(get()) }
    single { ProjectManager(get(), get()) }
    single { ArticleAnalyzer(get(), get(), get()) }
    single { RelevanceScorer(get(), getOrNull()) }
}

expect val platformModule: Module
