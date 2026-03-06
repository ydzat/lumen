package com.lumen.core.config

import kotlinx.serialization.json.Json
import java.io.File

actual class ConfigStore(private val configDir: File) {

    constructor() : this(File(System.getProperty("user.home"), ".lumen"))

    private val configFile: File
        get() = File(configDir, CONFIG_FILE_NAME)

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    actual fun load(): AppConfig {
        if (!configFile.exists()) return AppConfig()
        return try {
            json.decodeFromString<AppConfig>(configFile.readText())
        } catch (_: Exception) {
            AppConfig()
        }
    }

    actual fun save(config: AppConfig) {
        configDir.mkdirs()
        configFile.writeText(json.encodeToString(AppConfig.serializer(), config))
    }

    companion object {
        private const val CONFIG_FILE_NAME = "config.json"
    }
}
