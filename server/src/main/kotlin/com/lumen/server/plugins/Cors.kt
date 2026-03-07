package com.lumen.server.plugins

import com.lumen.server.config.EnvOverrides
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS

private val DEFAULT_HOSTS = listOf(
    "localhost:3000",
    "localhost:8080",
    "127.0.0.1:3000",
    "127.0.0.1:8080",
)

fun Application.configureCors() {
    val hosts = EnvOverrides.corsOrigins() ?: DEFAULT_HOSTS
    install(CORS) {
        for (host in hosts) {
            allowHost(host)
        }
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)
    }
}
