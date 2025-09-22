package com.mazekine.nekoton.oauth

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class McpOAuthRoutesTest {
    private val json = Json { ignoreUnknownKeys = false }

    private val config =
        McpOAuthConfig(
            issuer = "https://auth.example.com",
            authorizationEndpoint = "https://auth.example.com/oauth/authorize",
            tokenEndpoint = "https://auth.example.com/oauth/token",
            jwksUri = "https://auth.example.com/.well-known/jwks.json",
            userInfoEndpoint = "https://auth.example.com/userinfo",
            registrationEndpoint = "https://auth.example.com/register",
            introspectionEndpoint = "https://auth.example.com/introspect",
            revocationEndpoint = "https://auth.example.com/revoke",
            deviceAuthorizationEndpoint = "https://auth.example.com/device",
            scopes = listOf("mcp:read", "mcp:write"),
            resourceScopes = listOf("mcp:read"),
            responseTypes = listOf("code"),
            grantTypes = listOf("authorization_code", "refresh_token"),
            tokenEndpointAuthMethods = listOf("client_secret_post"),
            codeChallengeMethods = listOf("S256"),
            subjectTypes = listOf("public"),
            idTokenSigningAlgs = listOf("RS256"),
            claims = listOf("sub", "email"),
            authorizationServers = listOf("https://auth.example.com"),
            protectedResourceId = "https://api.example.com/mcp",
            resourceType = "model-context-protocol",
            resourceDocumentation = "https://docs.example.com/mcp",
            capabilities = mapOf("supports_sessions" to true, "requires_scopes" to true),
        )

    @Test
    fun `authorization server metadata is exposed`() = testApplication {
        application { configureMcpOAuthWellKnown(config) }

        val response =
            client.get("/.well-known/oauth-authorization-server") {
                header(HttpHeaders.Origin, "https://mcp.inspector")
            }

        assertEquals(HttpStatusCode.OK, response.status)
        val contentType = response.headers[HttpHeaders.ContentType]
        assertNotNull(contentType)
        assertTrue(contentType.startsWith(ContentType.Application.Json.toString()))
        assertEquals("https://mcp.inspector", response.headers[HttpHeaders.AccessControlAllowOrigin])

        val payload = json.decodeFromString<AuthorizationServerMetadata>(response.bodyAsText())
        assertEquals(config.issuer, payload.issuer)
        assertEquals(config.authorizationEndpoint, payload.authorizationEndpoint)
        assertEquals(config.tokenEndpoint, payload.tokenEndpoint)
        assertEquals(config.scopes, payload.scopesSupported)
        assertEquals(config.grantTypes, payload.grantTypesSupported)
        assertEquals(config.codeChallengeMethods, payload.codeChallengeMethodsSupported)
    }

    @Test
    fun `openid discovery metadata mirrors the authorization metadata`() = testApplication {
        application { configureMcpOAuthWellKnown(config) }

        val response = client.get("/.well-known/openid-configuration")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])

        val payload = json.decodeFromString<OpenIdConfigurationMetadata>(response.bodyAsText())
        assertEquals(config.issuer, payload.issuer)
        assertEquals(config.authorizationEndpoint, payload.authorizationEndpoint)
        assertEquals(config.tokenEndpoint, payload.tokenEndpoint)
        assertEquals(config.subjectTypes, payload.subjectTypesSupported)
        assertEquals(config.idTokenSigningAlgs, payload.idTokenSigningAlgValuesSupported)
        assertEquals(config.tokenEndpointAuthMethods, payload.tokenEndpointAuthMethodsSupported)
    }

    @Test
    fun `preflight requests are permitted`() = testApplication {
        application { configureMcpOAuthWellKnown(config) }

        val response =
            client.request("/.well-known/oauth-protected-resource/mcp") {
                method = HttpMethod.Options
                header(HttpHeaders.Origin, "https://tooling.example")
                header(HttpHeaders.AccessControlRequestHeaders, "authorization,content-type")
            }

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals("https://tooling.example", response.headers[HttpHeaders.AccessControlAllowOrigin])
        assertEquals("GET, OPTIONS", response.headers[HttpHeaders.AccessControlAllowMethods])
        val allowedHeaders = response.headers[HttpHeaders.AccessControlAllowHeaders]
        assertNotNull(allowedHeaders)
        assertTrue(allowedHeaders.contains("authorization", ignoreCase = true))
        assertTrue(allowedHeaders.contains("content-type", ignoreCase = true))
    }

    @Test
    fun `mcp protected resource metadata exposes capabilities`() = testApplication {
        application { configureMcpOAuthWellKnown(config) }

        val response = client.get("/.well-known/oauth-protected-resource/mcp")

        assertEquals(HttpStatusCode.OK, response.status)

        val payload = json.decodeFromString<McpProtectedResourceMetadata>(response.bodyAsText())
        assertEquals(config.protectedResourceId, payload.resource)
        assertEquals(config.authorizationServers, payload.authorizationServers)
        assertEquals(config.resourceScopes, payload.scopesSupported)
        assertEquals(config.capabilities, payload.capabilities)
    }
}
