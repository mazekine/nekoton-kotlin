package com.mazekine.nekoton.oauth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration describing how the MCP server exposes its OAuth 2.0/OpenID Connect metadata.
 *
 * The values provided here are rendered into the well-known discovery documents that
 * OAuth clients (such as the MCP Inspector) expect when bootstrapping the authorization flow.
 */
data class McpOAuthConfig(
    val issuer: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val jwksUri: String? = null,
    val userInfoEndpoint: String? = null,
    val registrationEndpoint: String? = null,
    val introspectionEndpoint: String? = null,
    val revocationEndpoint: String? = null,
    val deviceAuthorizationEndpoint: String? = null,
    val scopes: List<String>,
    val resourceScopes: List<String> = scopes,
    val responseTypes: List<String> = listOf("code"),
    val grantTypes: List<String> = listOf("authorization_code", "refresh_token"),
    val tokenEndpointAuthMethods: List<String> = listOf("client_secret_basic"),
    val codeChallengeMethods: List<String> = listOf("S256"),
    val subjectTypes: List<String> = listOf("public"),
    val idTokenSigningAlgs: List<String> = listOf("RS256"),
    val claims: List<String> = emptyList(),
    val authorizationServers: List<String> = listOf(issuer),
    val protectedResourceId: String = "mcp",
    val resourceType: String = "model-context-protocol",
    val resourceDocumentation: String? = null,
    val capabilities: Map<String, Boolean> = emptyMap(),
) {
    init {
        require(scopes.isNotEmpty()) { "At least one OAuth scope must be supplied" }
        require(resourceScopes.isNotEmpty()) { "At least one protected resource scope must be supplied" }
        require(authorizationServers.isNotEmpty()) { "At least one authorization server identifier must be supplied" }
    }

    internal fun asAuthorizationServerMetadata() =
        AuthorizationServerMetadata(
            issuer = issuer,
            authorizationEndpoint = authorizationEndpoint,
            tokenEndpoint = tokenEndpoint,
            jwksUri = jwksUri,
            registrationEndpoint = registrationEndpoint,
            deviceAuthorizationEndpoint = deviceAuthorizationEndpoint,
            responseTypesSupported = responseTypes,
            grantTypesSupported = grantTypes,
            scopesSupported = scopes,
            tokenEndpointAuthMethodsSupported = tokenEndpointAuthMethods,
            codeChallengeMethodsSupported = codeChallengeMethods,
            introspectionEndpoint = introspectionEndpoint,
            revocationEndpoint = revocationEndpoint,
            userInfoEndpoint = userInfoEndpoint,
        )

    internal fun asOpenIdConfiguration() =
        OpenIdConfigurationMetadata(
            issuer = issuer,
            authorizationEndpoint = authorizationEndpoint,
            tokenEndpoint = tokenEndpoint,
            jwksUri = jwksUri,
            userInfoEndpoint = userInfoEndpoint,
            responseTypesSupported = responseTypes,
            grantTypesSupported = grantTypes,
            scopesSupported = scopes,
            subjectTypesSupported = subjectTypes,
            idTokenSigningAlgValuesSupported = idTokenSigningAlgs,
            codeChallengeMethodsSupported = codeChallengeMethods,
            tokenEndpointAuthMethodsSupported = tokenEndpointAuthMethods,
            claimsSupported = claims,
        )

    internal fun asProtectedResourceMetadata() =
        ProtectedResourceMetadata(
            resource = protectedResourceId,
            authorizationServers = authorizationServers,
            scopesSupported = resourceScopes,
            tokenEndpointAuthMethodsSupported = tokenEndpointAuthMethods,
            resourceDocumentation = resourceDocumentation,
        )

    internal fun asMcpProtectedResourceMetadata() =
        McpProtectedResourceMetadata(
            resource = protectedResourceId,
            resourceType = resourceType,
            authorizationServers = authorizationServers,
            scopesSupported = resourceScopes,
            capabilities = capabilities,
        )
}

/** OAuth 2.0 Authorization Server metadata as defined in RFC 8414. */
@Serializable
data class AuthorizationServerMetadata(
    val issuer: String,
    @SerialName("authorization_endpoint") val authorizationEndpoint: String,
    @SerialName("token_endpoint") val tokenEndpoint: String,
    @SerialName("jwks_uri") val jwksUri: String? = null,
    @SerialName("registration_endpoint") val registrationEndpoint: String? = null,
    @SerialName("device_authorization_endpoint") val deviceAuthorizationEndpoint: String? = null,
    @SerialName("response_types_supported") val responseTypesSupported: List<String>,
    @SerialName("grant_types_supported") val grantTypesSupported: List<String>,
    @SerialName("scopes_supported") val scopesSupported: List<String>,
    @SerialName("token_endpoint_auth_methods_supported") val tokenEndpointAuthMethodsSupported: List<String>,
    @SerialName("code_challenge_methods_supported") val codeChallengeMethodsSupported: List<String>,
    @SerialName("introspection_endpoint") val introspectionEndpoint: String? = null,
    @SerialName("revocation_endpoint") val revocationEndpoint: String? = null,
    @SerialName("userinfo_endpoint") val userInfoEndpoint: String? = null,
)

/** OpenID Provider metadata as defined in the OpenID Connect Discovery specification. */
@Serializable
data class OpenIdConfigurationMetadata(
    val issuer: String,
    @SerialName("authorization_endpoint") val authorizationEndpoint: String,
    @SerialName("token_endpoint") val tokenEndpoint: String,
    @SerialName("jwks_uri") val jwksUri: String? = null,
    @SerialName("userinfo_endpoint") val userInfoEndpoint: String? = null,
    @SerialName("response_types_supported") val responseTypesSupported: List<String>,
    @SerialName("grant_types_supported") val grantTypesSupported: List<String>,
    @SerialName("scopes_supported") val scopesSupported: List<String>,
    @SerialName("subject_types_supported") val subjectTypesSupported: List<String>,
    @SerialName("id_token_signing_alg_values_supported") val idTokenSigningAlgValuesSupported: List<String>,
    @SerialName("code_challenge_methods_supported") val codeChallengeMethodsSupported: List<String>,
    @SerialName("token_endpoint_auth_methods_supported") val tokenEndpointAuthMethodsSupported: List<String>,
    @SerialName("claims_supported") val claimsSupported: List<String> = emptyList(),
)

/** Metadata describing a generic OAuth 2.0 protected resource (RFC 9470). */
@Serializable
data class ProtectedResourceMetadata(
    val resource: String,
    @SerialName("authorization_servers") val authorizationServers: List<String>,
    @SerialName("scopes_supported") val scopesSupported: List<String>,
    @SerialName("token_endpoint_auth_methods_supported")
    val tokenEndpointAuthMethodsSupported: List<String>,
    @SerialName("resource_documentation") val resourceDocumentation: String? = null,
)

/**
 * Additional metadata for the MCP protected resource variant expected by the MCP Inspector.
 */
@Serializable
data class McpProtectedResourceMetadata(
    val resource: String,
    @SerialName("resource_type") val resourceType: String,
    @SerialName("authorization_servers") val authorizationServers: List<String>,
    @SerialName("scopes_supported") val scopesSupported: List<String>,
    val capabilities: Map<String, Boolean> = emptyMap(),
)
