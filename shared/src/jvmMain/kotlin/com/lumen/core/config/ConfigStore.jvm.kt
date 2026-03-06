package com.lumen.core.config

import java.io.File

actual class ConfigStore(private val configDir: File) {

    constructor() : this(File(System.getProperty("user.home"), ".lumen"))

    private val configFile: File
        get() = File(configDir, CONFIG_FILE_NAME)

    actual fun load(): AppConfig {
        if (!configFile.exists()) return AppConfig()
        return try {
            configJson.decodeFromString<AppConfig>(configFile.readText())
        } catch (_: Exception) {
            AppConfig()
        }
    }

    actual fun save(config: AppConfig) {
        configDir.mkdirs()
        configFile.writeText(configJson.encodeToString(AppConfig.serializer(), config))
    }

    companion object {
        private const val CONFIG_FILE_NAME = "config.json"
    }
}
