package com.lumen.server.routes

import com.lumen.core.config.ConfigStore
import com.lumen.server.dto.SettingsUpdateRequest
import com.lumen.server.dto.toAppConfig
import com.lumen.server.dto.toSettingsDto
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.koin.ktor.ext.get as koinGet

fun Route.settingsRoutes() {
    route("/settings") {
        get {
            val configStore = call.application.koinGet<ConfigStore>()
            val config = configStore.load()
            call.respond(config.toSettingsDto())
        }

        put {
            val configStore = call.application.koinGet<ConfigStore>()
            val existing = configStore.load()
            val request = call.receive<SettingsUpdateRequest>()
            val updated = request.toAppConfig(existing)
            configStore.save(updated)
            call.respond(updated.toSettingsDto())
        }
    }
}
