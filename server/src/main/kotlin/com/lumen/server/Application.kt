package com.lumen.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import com.lumen.getPlatformName

fun main() {
    embeddedServer(Netty, port = 8000, module = Application::module).start(wait = true)
}

fun Application.module() {
    routing {
        get("/health") {
            call.respondText("Lumen Server running on ${getPlatformName()}", status = HttpStatusCode.OK)
        }
    }
}
