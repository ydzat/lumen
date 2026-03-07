package com.lumen.server.plugins

import com.lumen.companion.persona.PersonaManager
import com.lumen.core.config.ConfigStore
import com.lumen.core.database.PlatformDatabaseConfig
import com.lumen.core.database.createLumenDatabase
import com.lumen.core.di.companionModule
import com.lumen.core.di.documentModule
import com.lumen.core.di.memoryModule
import com.lumen.core.di.researchModule
import com.lumen.core.memory.EmbeddingClient
import com.lumen.core.memory.ModelResourceLoader
import com.lumen.core.memory.OnnxEmbeddingClient
import com.lumen.research.collector.PlatformScheduler
import com.lumen.server.config.EnvOverrides
import com.lumen.server.config.ServerConfigStore
import com.lumen.server.notification.NtfyNotifier
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.server.application.Application
import io.ktor.server.application.install
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module
import org.koin.ktor.ext.get as koinGet
import org.koin.ktor.plugin.Koin
import java.io.File

fun Application.configureKoin() {
    val serverDir = File(System.getProperty("user.home"), ".lumen/server")

    val serverPlatformModule = module {
        single {
            val store = ConfigStore(serverDir)
            EnvOverrides.bootstrapAppConfig(store)
            store
        }
        single { ModelResourceLoader() }
        single<EmbeddingClient> {
            OnnxEmbeddingClient(get())
        } withOptions { onClose { (it as? OnnxEmbeddingClient)?.close() } }
        single {
            val dbDir = File(serverDir, "db")
            createLumenDatabase(PlatformDatabaseConfig(dbDir))
        } withOptions { onClose { it?.close() } }
        single { PlatformScheduler(get()) }
        single { ServerConfigStore(serverDir) }
        single { HttpClient(CIO) } withOptions { onClose { it?.close() } }
        single { NtfyNotifier(get(), get()) }
    }

    install(Koin) {
        modules(
            serverPlatformModule,
            companionModule,
            memoryModule,
            researchModule,
            documentModule,
        )
    }

    koinGet<PersonaManager>().seedBuiltInPersonas()
}
