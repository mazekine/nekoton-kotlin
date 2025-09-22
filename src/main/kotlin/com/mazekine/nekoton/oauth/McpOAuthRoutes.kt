package com.mazekine.nekoton.oauth

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.options
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val metadataJson = Json { encodeDefaults = true }

/**
 * Installs well-known OAuth/OpenID discovery endpoints required by the MCP Inspector.
 *
 * The generated endpoints answer:
 * - /.well-known/oauth-authorization-server
 * - /.well-known/openid-configuration
 * - /.well-known/oauth-protected-resource
 * - /.well-known/oauth-protected-resource/mcp
 */
fun Application.configureMcpOAuthWellKnown(config: McpOAuthConfig) {
    routing {
        route("/.well-known") {
            wellKnownEndpoint("oauth-authorization-server") { config.asAuthorizationServerMetadata() }
            wellKnownEndpoint("openid-configuration") { config.asOpenIdConfiguration() }
            wellKnownEndpoint("oauth-protected-resource") { config.asProtectedResourceMetadata() }
            wellKnownEndpoint("oauth-protected-resource/mcp") { config.asMcpProtectedResourceMetadata() }
        }
    }
}

private inline fun <reified T> Route.wellKnownEndpoint(
    path: String,
    crossinline payloadProvider: () -> T,
) {
    route(path) {
        options {
            call.applyCorsHeaders()
            call.respond(HttpStatusCode.NoContent)
        }
        get {
            call.applyCorsHeaders()
            val payload = payloadProvider()
            call.respondText(metadataJson.encodeToString(payload), ContentType.Application.Json)
        }
    }
}

private fun ApplicationCall.applyCorsHeaders() {
    val origin = request.headers[HttpHeaders.Origin]
    if (origin != null) {
        response.headers.append(HttpHeaders.AccessControlAllowOrigin, origin, false)
        response.headers.append(HttpHeaders.Vary, HttpHeaders.Origin, false)
        response.headers.append(HttpHeaders.AccessControlAllowCredentials, "true", false)
    } else {
        response.headers.append(HttpHeaders.AccessControlAllowOrigin, "*", false)
    }

    response.headers.append(HttpHeaders.AccessControlAllowMethods, "GET, ${HttpMethod.Options.value}", false)

    val requestedHeaders = request.headers[HttpHeaders.AccessControlRequestHeaders]
    if (!requestedHeaders.isNullOrBlank()) {
        response.headers.append(HttpHeaders.AccessControlAllowHeaders, requestedHeaders, false)
    } else {
        response.headers.append(HttpHeaders.AccessControlAllowHeaders, "Authorization, Content-Type", false)
    }
}
