package com.lumen.companion.agent

import com.lumen.core.config.LlmConfig
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNull

class TitleGenerationTest {

    @Test
    fun generateTitle_withEmptyApiKey_returnsNull() {
        runBlocking {
            val agent = LumenAgent(LlmConfig(apiKey = ""))
            try {
                val title = agent.generateTitle("Hello, how are you?")
                assertNull(title)
            } finally {
                agent.close()
            }
        }
    }

    @Test
    fun defaultTitle_constant_matchesExpected() {
        assert(LumenAgent.DEFAULT_TITLE == "New conversation")
    }
}
