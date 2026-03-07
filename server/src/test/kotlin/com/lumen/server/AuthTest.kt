package com.lumen.server

import com.lumen.server.plugins.AUTH_BEARER
import com.lumen.server.plugins.configureAuth
import com.lumen.server.plugins.configureErrorHandling
import com.lumen.server.plugins.configureSerialization
import com.lumen.server.routes.HealthResponse
import com.lumen.server.routes.healthRoutes
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val testToken = "test-secret-token-12345"

    @Test
    fun protectedRoute_withoutToken_returns401() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            routing {
                authenticate(AUTH_BEARER) {
                    get("/api/test") {
                        call.respondText("ok")
                    }
                }
            }
        }
        val response = client.get("/api/test")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun protectedRoute_withInvalidToken_returns401() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            routing {
                authenticate(AUTH_BEARER) {
                    get("/api/test") {
                        call.respondText("ok")
                    }
                }
            }
        }
        val response = client.get("/api/test") {
            bearerAuth("wrong-token")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun protectedRoute_withValidToken_returns200() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            configureAuth(testToken)
            routing {
                authenticate(AUTH_BEARER) {
                    get("/api/test") {
                        call.respondText("ok")
                    }
                }
            }
        }
        val response = client.get("/api/test") {
            bearerAuth(testToken)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", response.bodyAsText())
    }

    @Test
    fun health_withoutToken_returnsOk() = testApplication {
        application {
            configureSerialization()
            configureAuth(testToken)
            routing {
                healthRoutes()
                authenticate(AUTH_BEARER) {
                    get("/api/test") {
                        call.respondText("ok")
                    }
                }
            }
        }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<HealthResponse>(response.bodyAsText())
        assertEquals("ok", body.status)
    }
}
