package com.lumen.core.config

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

actual class ConfigStore(private val context: Context) {

    private val configFile: File
        get() = File(context.filesDir, CONFIG_FILE_NAME)

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
        configFile.writeText(json.encodeToString(AppConfig.serializer(), config))
    }

    companion object {
        private const val CONFIG_FILE_NAME = "lumen-config.json"
    }
}
