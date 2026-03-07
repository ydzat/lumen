package com.lumen.server.routes

import io.ktor.server.routing.Route
import io.ktor.server.routing.route

fun Route.apiRoutes() {
    route("/api") {
        route("/research") {
            // #83: Research REST API
        }
        route("/chat") {
            // #84: Chat REST API
        }
        route("/settings") {
            // #85: Settings REST API
        }
        route("/documents") {
            // #86: Document Upload REST API
        }
    }
}
