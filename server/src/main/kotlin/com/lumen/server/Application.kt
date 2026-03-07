package com.lumen.server

import com.lumen.server.plugins.configureCors
import com.lumen.server.plugins.configureErrorHandling
import com.lumen.server.plugins.configureKoin
import com.lumen.server.plugins.configureLogging
import com.lumen.server.plugins.configureSerialization
import com.lumen.server.routes.apiRoutes
import com.lumen.server.routes.healthRoutes
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing

fun main() {
    embeddedServer(Netty, port = 8000, module = Application::module).start(wait = true)
}

fun Application.module() {
    configureSerialization()
    configureErrorHandling()
    configureCors()
    configureLogging()
    configureKoin()
    routing {
        healthRoutes()
        apiRoutes()
    }
}
