package com.lumen.companion.agent

import com.lumen.core.config.LlmConfig
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LumenAgentTest {

    private fun loadEnvConfig(): LlmConfig {
        val envFile = File(System.getProperty("user.dir")).resolve("../.env").canonicalFile
        if (!envFile.exists()) return LlmConfig()

        val env = envFile.readLines()
            .filter { it.contains("=") }
            .associate {
                val (key, value) = it.split("=", limit = 2)
                key.trim() to value.trim()
            }

        return LlmConfig(
            provider = "deepseek",
            model = env["MODEL"] ?: "deepseek-chat",
            apiKey = env["APIKEY"] ?: ""
        )
    }

    @Test
    fun chat_withEmptyApiKey_returnsError() {
        runBlocking {
            val agent = LumenAgent(LlmConfig(apiKey = ""))
            try {
                val result = agent.chat("Hello")
                assertIs<ChatResult.Error>(result)
                assertTrue(result.message.contains("API key"), "Expected API key error, got: ${result.message}")
            } finally {
                agent.close()
            }
        }
    }

    @Test
    fun chat_withInvalidApiKey_returnsError() {
        runBlocking {
            val agent = LumenAgent(LlmConfig(apiKey = "sk-invalid-key-for-testing"))
            try {
                val result = agent.chat("Hello")
                assertIs<ChatResult.Error>(result)
            } finally {
                agent.close()
            }
        }
    }

    @Test
    fun chat_withValidConfig_returnsResponse() {
        runBlocking {
            val config = loadEnvConfig()
            if (config.apiKey.isBlank()) {
                println("Skipping live API test: no APIKEY in .env")
                return@runBlocking
            }

            val agent = LumenAgent(config)
            try {
                val result = agent.chat("What is 2+2? Reply with just the number.")
                assertIs<ChatResult.Success>(result)
                assertTrue(result.response.contains("4"), "Expected response to contain '4', got: ${result.response}")
            } finally {
                agent.close()
            }
        }
    }
}
