package com.lumen.server

import com.lumen.server.plugins.ErrorResponse
import com.lumen.server.plugins.NotFoundException
import com.lumen.server.plugins.configureCors
import com.lumen.server.plugins.configureErrorHandling
import com.lumen.server.plugins.configureSerialization
import com.lumen.server.routes.HealthResponse
import com.lumen.server.routes.healthRoutes
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ApplicationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun health_returnsOkJson() = testApplication {
        application {
            configureSerialization()
            routing { healthRoutes() }
        }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<HealthResponse>(response.bodyAsText())
        assertEquals("ok", body.status)
        assertNotNull(body.platform)
    }

    @Test
    fun illegalArgumentException_returns400Json() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            routing {
                get("/test-bad-request") {
                    throw IllegalArgumentException("invalid param")
                }
            }
        }
        val response = client.get("/test-bad-request")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = json.decodeFromString<ErrorResponse>(response.bodyAsText())
        assertEquals(400, body.status)
        assertEquals("invalid param", body.error)
    }

    @Test
    fun notFoundException_returns404Json() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            routing {
                get("/test-not-found") {
                    throw NotFoundException("item not found")
                }
            }
        }
        val response = client.get("/test-not-found")
        assertEquals(HttpStatusCode.NotFound, response.status)
        val body = json.decodeFromString<ErrorResponse>(response.bodyAsText())
        assertEquals(404, body.status)
        assertEquals("item not found", body.error)
    }

    @Test
    fun genericException_returns500Json() = testApplication {
        application {
            configureSerialization()
            configureErrorHandling()
            routing {
                get("/test-error") {
                    throw RuntimeException("unexpected failure")
                }
            }
        }
        val response = client.get("/test-error")
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        val body = json.decodeFromString<ErrorResponse>(response.bodyAsText())
        assertEquals(500, body.status)
        assertEquals("unexpected failure", body.error)
    }

    @Test
    fun cors_allowsLocalhostOrigin() = testApplication {
        application {
            configureSerialization()
            configureCors()
            routing { healthRoutes() }
        }
        val response = client.options("/health") {
            header(HttpHeaders.Origin, "http://localhost:3000")
            header(HttpHeaders.AccessControlRequestMethod, HttpMethod.Get.value)
        }
        val allowOrigin = response.headers[HttpHeaders.AccessControlAllowOrigin]
        assertNotNull(allowOrigin)
        assertEquals("http://localhost:3000", allowOrigin)
    }
}
