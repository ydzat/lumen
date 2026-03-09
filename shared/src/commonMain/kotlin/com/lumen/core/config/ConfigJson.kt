package com.lumen.core.config

import kotlinx.serialization.json.Json

internal val configJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}
