package com.lumen.server.config

import com.lumen.core.config.AppConfig
import com.lumen.core.config.ConfigStore

object EnvOverrides {

    fun applyToServerConfig(config: ServerConfig): ServerConfig = config.copy(
        accessToken = env("LUMEN_ACCESS_TOKEN") ?: config.accessToken,
        ntfyServerUrl = env("LUMEN_NTFY_URL") ?: config.ntfyServerUrl,
        ntfyTopic = env("LUMEN_NTFY_TOPIC") ?: config.ntfyTopic,
    )

    fun bootstrapAppConfig(store: ConfigStore) {
        val apiKey = env("LUMEN_LLM_API_KEY") ?: return
        val config = store.load()
        if (config.llm.apiKey != apiKey) {
            store.save(config.copy(llm = config.llm.copy(apiKey = apiKey)))
        }
    }

    fun corsOrigins(): List<String>? {
        val raw = env("LUMEN_CORS_ORIGINS") ?: return null
        return raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "*" }
            .ifEmpty { null }
    }

    private fun env(name: String): String? =
        System.getenv(name)?.takeIf { it.isNotBlank() }
}
