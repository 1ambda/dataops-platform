package com.dataops.basecamp.config

import com.dataops.basecamp.common.constant.CommonConstants
import com.dataops.basecamp.filter.MockAuthenticationFilter
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

private val logger = KotlinLogging.logger {}

/**
 * Security configuration for the application.
 *
 * Supports two authentication modes:
 * 1. **OAuth2/JWT Mode** (default): Validates JWT tokens from Keycloak
 * 2. **Mock Mode** (local/test): Uses MockAuthenticationFilter with request headers
 *
 * ## Configuration
 * - `app.security.mock-auth-enabled=true`: Enable mock authentication
 * - `app.security.mock-auth-enabled=false`: Use OAuth2/JWT authentication
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties::class)
class SecurityConfig(
    private val securityProperties: SecurityProperties,
) {
    /**
     * Security filter chain for Mock authentication mode.
     * Active when `app.security.mock-auth-enabled=true`.
     */
    @Bean
    @ConditionalOnProperty(
        name = ["app.security.mock-auth-enabled"],
        havingValue = "true",
        matchIfMissing = false,
    )
    fun mockSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        logger.info { "Configuring MOCK authentication mode (OAuth2 disabled)" }

        return http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(*publicEndpoints())
                    .permitAll()
                    .requestMatchers(HttpMethod.OPTIONS)
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.addFilterBefore(
                MockAuthenticationFilter(securityProperties),
                UsernamePasswordAuthenticationFilter::class.java,
            ).build()
    }

    /**
     * Security filter chain for OAuth2/JWT authentication mode.
     * Active when `app.security.mock-auth-enabled=false` (default).
     */
    @Bean
    @ConditionalOnProperty(
        name = ["app.security.mock-auth-enabled"],
        havingValue = "false",
        matchIfMissing = true,
    )
    fun oauth2SecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        logger.info { "Configuring OAuth2/JWT authentication mode" }

        return http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(*publicEndpoints())
                    .permitAll()
                    .requestMatchers(HttpMethod.OPTIONS)
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(keycloakJwtAuthenticationConverter())
                }
            }.build()
    }

    /**
     * Public endpoints that don't require authentication.
     */
    private fun publicEndpoints(): Array<String> =
        arrayOf(
            "${CommonConstants.Api.BASE_PATH}${CommonConstants.Api.HEALTH_PATH}",
            "${CommonConstants.Api.BASE_PATH}/info",
            "/actuator/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/error",
        )

    /**
     * Converts Keycloak JWT to Spring Security authentication with proper role extraction.
     *
     * Keycloak stores roles in different locations depending on configuration:
     * 1. `realm_access.roles` - Realm-level roles
     * 2. `resource_access.<client>.roles` - Client-specific roles
     */
    @Bean
    fun keycloakJwtAuthenticationConverter(): Converter<Jwt, AbstractAuthenticationToken> =
        Converter { jwt ->
            val authorities = extractAuthorities(jwt)
            JwtAuthenticationToken(jwt, authorities, jwt.subject)
        }

    private fun extractAuthorities(jwt: Jwt): Collection<GrantedAuthority> {
        val authorities = mutableListOf<GrantedAuthority>()

        // Extract realm roles from realm_access.roles
        val realmAccess = jwt.getClaim<Map<String, Any>>("realm_access")
        if (realmAccess != null) {
            @Suppress("UNCHECKED_CAST")
            val realmRoles = realmAccess["roles"] as? List<String> ?: emptyList()
            realmRoles.forEach { role ->
                authorities.add(SimpleGrantedAuthority("ROLE_$role"))
            }
        }

        // Extract client roles from resource_access.<client>.roles
        val resourceAccess = jwt.getClaim<Map<String, Any>>("resource_access")
        if (resourceAccess != null) {
            resourceAccess.forEach { (_, clientAccess) ->
                @Suppress("UNCHECKED_CAST")
                val clientMap = clientAccess as? Map<String, Any>

                @Suppress("UNCHECKED_CAST")
                val clientRoles = clientMap?.get("roles") as? List<String> ?: emptyList()
                clientRoles.forEach { role ->
                    authorities.add(SimpleGrantedAuthority("ROLE_$role"))
                }
            }
        }

        // Also add scope-based authorities (optional, for fine-grained access control)
        val scopes = jwt.getClaim<String>("scope")
        scopes?.split(" ")?.forEach { scope ->
            authorities.add(SimpleGrantedAuthority("SCOPE_$scope"))
        }

        return authorities.distinct()
    }
}
