package com.lumen.core.config

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigStoreTest {

    private lateinit var tempDir: File
    private lateinit var store: ConfigStore

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "lumen-config-test-${System.nanoTime()}")
        tempDir.mkdirs()
        store = ConfigStore(tempDir)
    }

    @AfterTest
    fun teardown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun load_withNoFile_returnsDefaults() {
        val config = store.load()
        assertEquals("deepseek", config.llm.provider)
        assertEquals("deepseek-chat", config.llm.model)
        assertEquals("", config.llm.apiKey)
        assertEquals("", config.llm.apiBase)
        assertEquals("zh", config.preferences.language)
        assertEquals("system", config.preferences.theme)
    }

    @Test
    fun saveAndLoad_roundTrips() {
        val config = AppConfig(
            llm = LlmConfig(
                provider = "openai",
                model = "gpt-4o",
                apiKey = "sk-test-key",
                apiBase = "https://custom.api.com/v1"
            ),
            preferences = UserPreferences(
                language = "en",
                theme = "dark"
            )
        )
        store.save(config)
        val loaded = store.load()

        assertEquals(config, loaded)
    }

    @Test
    fun save_overwritesPreviousConfig() {
        store.save(AppConfig(llm = LlmConfig(provider = "openai")))
        store.save(AppConfig(llm = LlmConfig(provider = "anthropic")))

        val loaded = store.load()
        assertEquals("anthropic", loaded.llm.provider)
    }

    @Test
    fun load_withCorruptedFile_returnsDefaults() {
        val configFile = File(tempDir, "config.json")
        configFile.writeText("not valid json {{{")

        val config = store.load()
        assertEquals(AppConfig(), config)
    }

    @Test
    fun save_createsDirectoryIfNeeded() {
        val nestedDir = File(tempDir, "nested/deep")
        val nestedStore = ConfigStore(nestedDir)
        nestedStore.save(AppConfig(llm = LlmConfig(apiKey = "test")))

        val loaded = nestedStore.load()
        assertEquals("test", loaded.llm.apiKey)
    }
}
