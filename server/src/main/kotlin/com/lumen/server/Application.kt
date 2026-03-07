package com.lumen.server

import com.lumen.research.collector.PlatformScheduler
import com.lumen.server.plugins.configureAuth
import com.lumen.server.plugins.configureCors
import com.lumen.server.plugins.configureErrorHandling
import com.lumen.server.plugins.configureKoin
import com.lumen.server.plugins.configureLogging
import com.lumen.server.plugins.configureSerialization
import com.lumen.server.routes.apiRoutes
import com.lumen.server.routes.healthRoutes
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import org.koin.ktor.ext.get as koinGet

fun main() {
    embeddedServer(Netty, port = 8000, module = Application::module).start(wait = true)
}

fun Application.module() {
    configureSerialization()
    configureErrorHandling()
    configureCors()
    configureLogging()
    configureAuth()
    configureKoin()
    routing {
        healthRoutes()
        apiRoutes()
    }
    startScheduler()
}

private fun Application.startScheduler() {
    val scheduler = koinGet<PlatformScheduler>()
    scheduler.start()
    log.info("PlatformScheduler started (interval: ${PlatformScheduler.DEFAULT_INTERVAL / 3600000}h)")
    monitor.subscribe(ApplicationStopped) {
        scheduler.stop()
    }
}
