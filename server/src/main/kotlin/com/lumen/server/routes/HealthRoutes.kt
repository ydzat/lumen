package com.lumen.server.routes

import com.lumen.getPlatformName
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String, val platform: String)

fun Route.healthRoutes() {
    get("/health") {
        call.respond(HealthResponse(status = "ok", platform = getPlatformName()))
    }
}
