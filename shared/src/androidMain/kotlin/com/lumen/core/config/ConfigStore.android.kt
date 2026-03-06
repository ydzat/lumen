package com.lumen.core.config

import android.content.Context
import java.io.File

actual class ConfigStore(private val context: Context) {

    private val configFile: File
        get() = File(context.filesDir, CONFIG_FILE_NAME)

    actual fun load(): AppConfig {
        if (!configFile.exists()) return AppConfig()
        return try {
            configJson.decodeFromString<AppConfig>(configFile.readText())
        } catch (_: Exception) {
            AppConfig()
        }
    }

    actual fun save(config: AppConfig) {
        context.filesDir.mkdirs()
        configFile.writeText(configJson.encodeToString(AppConfig.serializer(), config))
    }

    companion object {
        private const val CONFIG_FILE_NAME = "lumen-config.json"
    }
}
