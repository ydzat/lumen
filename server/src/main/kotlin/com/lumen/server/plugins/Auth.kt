package com.lumen.server.plugins

import com.lumen.server.config.ServerConfigStore
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.bearer
import java.io.File

const val AUTH_BEARER = "auth-bearer"

fun Application.configureAuth() {
    val serverDir = File(System.getProperty("user.home"), ".lumen/server")
    val configStore = ServerConfigStore(serverDir)
    val accessToken = configStore.ensureAccessToken()

    val masked = if (accessToken.length > 4) accessToken.take(4) + "****" else "****"
    log.info("=== Lumen Server Access Token: $masked ===")

    configureAuth(accessToken)
}

fun Application.configureAuth(accessToken: String) {
    install(Authentication) {
        bearer(AUTH_BEARER) {
            authenticate { tokenCredential ->
                if (tokenCredential.token == accessToken) {
                    UserIdPrincipal("lumen-user")
                } else {
                    null
                }
            }
        }
    }
}
