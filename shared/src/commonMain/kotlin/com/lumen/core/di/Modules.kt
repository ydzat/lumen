package com.lumen.core.di

import com.lumen.companion.agent.LlmClientFactory
import com.lumen.companion.agent.LumenAgent
import com.lumen.core.config.ConfigStore
import com.lumen.core.database.LumenDatabase
import com.lumen.core.memory.EmbeddingClient
import com.lumen.core.memory.IntentRetriever
import com.lumen.core.memory.KoogLlmCall
import com.lumen.core.memory.LlmCall
import com.lumen.core.memory.MemoryManager
import com.lumen.core.memory.SemanticCompressor
import com.lumen.core.memory.SemanticSynthesizer
import com.lumen.research.ProjectManager
import com.lumen.research.analyzer.ArticleAnalyzer
import com.lumen.research.analyzer.RelevanceScorer
import com.lumen.research.collector.CollectorManager
import com.lumen.research.collector.RssCollector
import com.lumen.research.collector.SourceManager
import com.lumen.research.digest.DigestFormatter
import com.lumen.research.digest.DigestGenerator
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

val companionModule = module {
    factory {
        val config = get<ConfigStore>().load().llm
        val memoryManager = getOrNull<MemoryManager>()
        val db = getOrNull<LumenDatabase>()
        val embeddingClient = getOrNull<EmbeddingClient>()
        LumenAgent(config, memoryManager, db, embeddingClient)
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
    single { DigestGenerator(get(), get(), getOrNull()) }
    single { DigestFormatter() }
    single { CollectorManager(get(), get(), get(), get()) }
}

expect val platformModule: Module
