package com.lumen.server.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class ServerConfig(
    val accessToken: String = "",
)

class ServerConfigStore(private val configDir: File) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val configFile: File
        get() = File(configDir, CONFIG_FILE_NAME)

    fun load(): ServerConfig {
        if (!configFile.exists()) return ServerConfig()
        return try {
            json.decodeFromString<ServerConfig>(configFile.readText())
        } catch (_: Exception) {
            ServerConfig()
        }
    }

    fun save(config: ServerConfig) {
        configDir.mkdirs()
        configFile.writeText(json.encodeToString(ServerConfig.serializer(), config))
    }

    fun ensureAccessToken(): String {
        val config = load()
        if (config.accessToken.isNotBlank()) return config.accessToken
        val token = UUID.randomUUID().toString()
        save(config.copy(accessToken = token))
        return token
    }

    companion object {
        private const val CONFIG_FILE_NAME = "server-config.json"
    }
}
