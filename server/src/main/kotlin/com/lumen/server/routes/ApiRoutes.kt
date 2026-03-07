package com.lumen.server.routes

import com.lumen.server.plugins.AUTH_BEARER
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

fun Route.apiRoutes() {
    authenticate(AUTH_BEARER) {
        route("/api") {
            articleRoutes()
            sourceRoutes()
            projectRoutes()
            digestRoutes()
            chatRoutes()
            personaRoutes()
            settingsRoutes()
            route("/documents") {
                // #86: Document Upload REST API
            }
        }
    }
}
