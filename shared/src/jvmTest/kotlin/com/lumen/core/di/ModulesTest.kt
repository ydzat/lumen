package com.lumen.core.di

import com.lumen.companion.agent.LumenAgent
import com.lumen.core.config.ConfigStore
import com.lumen.core.database.LumenDatabase
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

class ModulesTest {

    @BeforeTest
    fun setup() {
        startKoin {
            modules(platformModule, companionModule, researchModule)
        }
    }

    @AfterTest
    fun teardown() {
        stopKoin()
    }

    @Test
    fun platformModule_providesConfigStore() {
        val configStore = org.koin.java.KoinJavaComponent.getKoin().get<ConfigStore>()
        assertNotNull(configStore)
    }

    @Test
    fun platformModule_providesLumenDatabase() {
        val database = org.koin.java.KoinJavaComponent.getKoin().get<LumenDatabase>()
        assertNotNull(database)
        database.close()
    }

    @Test
    fun companionModule_providesLumenAgent() {
        val agent = org.koin.java.KoinJavaComponent.getKoin().get<LumenAgent>()
        assertNotNull(agent)
        agent.close()
    }
}
