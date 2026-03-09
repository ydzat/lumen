package com.lumen.server.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val error: String, val status: Int)

class NotFoundException(message: String) : RuntimeException(message)
class PayloadTooLargeException(message: String) : RuntimeException(message)

fun Application.configureErrorHandling() {
    install(StatusPages) {
        exception<PayloadTooLargeException> { call, cause ->
            call.respond(
                HttpStatusCode.PayloadTooLarge,
                ErrorResponse(cause.message ?: "Payload too large", 413),
            )
        }
        exception<NotFoundException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(cause.message ?: "Not found", 404),
            )
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(cause.message ?: "Bad request", 400),
            )
        }
        exception<Exception> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(cause.message ?: "Internal server error", 500),
            )
        }
    }
}
