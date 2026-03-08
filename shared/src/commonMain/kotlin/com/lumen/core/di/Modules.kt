package com.lumen.core.di

import com.lumen.companion.agent.ContextWindowBuilder
import com.lumen.companion.agent.LlmClientFactory
import com.lumen.companion.agent.LumenAgent
import com.lumen.companion.conversation.ConversationManager
import com.lumen.companion.persona.PersonaManager
import com.lumen.core.config.ConfigStore
import com.lumen.core.database.LumenDatabase
import com.lumen.core.document.DocumentIngestionService
import com.lumen.core.document.DocumentManager
import com.lumen.core.document.DocumentParser
import com.lumen.core.document.TextChunker
import com.lumen.core.memory.EmbeddingClient
import com.lumen.core.memory.IntentRetriever
import com.lumen.core.memory.KoogLlmCall
import com.lumen.core.memory.LlmCall
import com.lumen.core.memory.MemoryManager
import com.lumen.core.memory.SemanticCompressor
import com.lumen.core.memory.SemanticSynthesizer
import com.lumen.research.ProjectManager
import com.lumen.research.analyzer.ArticleAnalyzer
import com.lumen.research.analyzer.DeepAnalysisService
import com.lumen.research.analyzer.RelevanceScorer
import com.lumen.research.collector.ArxivApiDataSource
import com.lumen.research.archiver.ArticleArchiver
import com.lumen.research.collector.CollectorManager
import com.lumen.research.collector.ContentEnricher
import com.lumen.research.collector.DataSource
import com.lumen.research.collector.Deduplicator
import com.lumen.research.collector.GitHubReleasesDataSource
import com.lumen.research.collector.RssDataSource
import com.lumen.research.collector.ScholarDataSource
import com.lumen.research.collector.SourceManager
import com.lumen.research.digest.DigestFormatter
import com.lumen.research.digest.DigestGenerator
import com.lumen.research.spark.SparkEngine
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import org.koin.core.module.Module
import org.koin.dsl.module

val companionModule = module {
    single { ConversationManager(get()) }
    single { ContextWindowBuilder(getOrNull()) }
    single { PersonaManager(get()) }
    factory { (projectId: Long, personaId: Long) ->
        val appConfig = get<ConfigStore>().load()
        val memoryManager = getOrNull<MemoryManager>()
        val db = getOrNull<LumenDatabase>()
        val embeddingClient = getOrNull<EmbeddingClient>()
        val conversationManager = getOrNull<ConversationManager>()
        val contextWindowBuilder = getOrNull<ContextWindowBuilder>()
        val personaManager = getOrNull<PersonaManager>()
        val persona = if (personaId > 0) personaManager?.get(personaId) else personaManager?.getActive()
        LumenAgent(appConfig.llm, memoryManager, db, embeddingClient, conversationManager, contextWindowBuilder, persona, appConfig.preferences, projectId)
    }
}

val memoryModule = module {
    factory<LlmCall> {
        val config = get<ConfigStore>().load().llm
        val httpClient = HttpClient {
            install(HttpTimeout) {
                connectTimeoutMillis = 30_000
                requestTimeoutMillis = 120_000
                socketTimeoutMillis = 120_000
            }
        }
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
    single { RssDataSource(get()) }
    single { SourceManager(get()) }
    single { ProjectManager(get(), get()) }
    single { ArticleAnalyzer(get(), get(), get()) }
    single { DeepAnalysisService(get(), get()) }
    single { RelevanceScorer(get(), getOrNull()) }
    single { DigestGenerator(get(), get(), getOrNull(), getOrNull(), getOrNull()) }
    single { DigestFormatter() }
    single { Deduplicator(get()) }
    single { SparkEngine(get(), get(), getOrNull()) }
    single { ArticleArchiver(get(), getOrNull()) }
    single {
        ArxivApiDataSource(
            db = get(),
            httpClient = HttpClient {
                install(HttpTimeout) {
                    connectTimeoutMillis = 30_000
                    requestTimeoutMillis = 60_000
                    socketTimeoutMillis = 60_000
                }
            },
        )
    }
    single {
        ScholarDataSource(
            db = get(),
            httpClient = HttpClient {
                install(HttpTimeout) {
                    connectTimeoutMillis = 30_000
                    requestTimeoutMillis = 60_000
                    socketTimeoutMillis = 60_000
                }
            },
        )
    }
    single {
        GitHubReleasesDataSource(
            db = get(),
            httpClient = HttpClient {
                install(HttpTimeout) {
                    connectTimeoutMillis = 30_000
                    requestTimeoutMillis = 60_000
                    socketTimeoutMillis = 60_000
                }
            },
        )
    }
    single<List<DataSource>> { listOf(get<RssDataSource>(), get<ArxivApiDataSource>(), get<ScholarDataSource>(), get<GitHubReleasesDataSource>()) }
    single {
        ContentEnricher(
            httpClient = HttpClient {
                install(HttpTimeout) {
                    connectTimeoutMillis = 30_000
                    requestTimeoutMillis = 60_000
                    socketTimeoutMillis = 60_000
                }
            },
            db = get(),
        )
    }
    single {
        CollectorManager(
            articleAnalyzer = get(),
            relevanceScorer = get(),
            digestGenerator = get(),
            dataSources = get(),
            sourceManager = get(),
            deduplicator = get(),
            db = getOrNull(),
            projectManager = getOrNull(),
            sparkEngine = getOrNull(),
            articleArchiver = getOrNull(),
            contentEnricher = getOrNull(),
        )
    }
}

val documentModule = module {
    single { DocumentParser() }
    single { TextChunker() }
    single { DocumentIngestionService(get(), get(), get(), get()) }
    single { DocumentManager(get(), get()) }
}

expect val platformModule: Module
