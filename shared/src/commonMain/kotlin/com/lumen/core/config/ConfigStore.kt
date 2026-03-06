package com.lumen.core.config

expect class ConfigStore {
    fun load(): AppConfig
    fun save(config: AppConfig)
}
