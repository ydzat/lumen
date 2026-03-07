package com.lumen.core.di

import com.lumen.companion.agent.LumenAgent
import com.lumen.core.config.ConfigStore
import com.lumen.core.database.LumenDatabase
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
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
        val configStore = GlobalContext.get().get<ConfigStore>()
        assertNotNull(configStore)
    }

    @Test
    fun platformModule_providesLumenDatabase() {
        val database = GlobalContext.get().get<LumenDatabase>()
        assertNotNull(database)
    }

    @Test
    fun companionModule_providesLumenAgent() {
        val agent = GlobalContext.get().get<LumenAgent> { parametersOf(0L, 0L) }
        assertNotNull(agent)
        agent.close()
    }
}
