package com.fancia.backend.user.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver
import org.springframework.security.web.SecurityFilterChain

@EnableMethodSecurity
@EnableWebSecurity
@Configuration
class SecurityConfiguration {
    @Bean
    fun bearerTokenResolver(): BearerTokenResolver {
        val defaultResolver = DefaultBearerTokenResolver()
        return BearerTokenResolver { request ->
            val path = request.requestURI.removePrefix(request.contextPath ?: "")
            if (path.startsWith("/api/v1/webhooks/") || path.startsWith("/api/webhooks/")) {
                null
            } else {
                defaultResolver.resolve(request)
            }
        }
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        bearerTokenResolver: BearerTokenResolver,
    ): SecurityFilterChain {
        http.authorizeHttpRequests { customizer ->
            customizer.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            customizer.requestMatchers(HttpMethod.POST, "/api/v1/webhooks/**").permitAll()
            customizer.requestMatchers("/api/v1/smart-match", "/api/v1/smart-match/**").authenticated()
            customizer.requestMatchers("/api/v1/chat", "/api/v1/chat/**").authenticated()
            customizer.requestMatchers("/api/v1/friends", "/api/v1/friends/**").authenticated()
            customizer.requestMatchers(HttpMethod.GET, "/api/v1/users/**").permitAll()
            customizer.requestMatchers(HttpMethod.POST, "/api/v1/users").permitAll()
            customizer.requestMatchers(HttpMethod.POST, "/api/v1/users/forgot-password").permitAll()
            customizer.requestMatchers(HttpMethod.PATCH, "/api/v1/users/reset-password").permitAll()
            customizer.requestMatchers(HttpMethod.PUT, "/internal/v1/users/*/premium").permitAll()
            customizer.requestMatchers(HttpMethod.GET, "/internal/v1/users/*/blocked").permitAll()
            customizer.requestMatchers(HttpMethod.GET, "/internal/v1/users/*").permitAll()
            customizer.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
            customizer.requestMatchers("/actuator/**").permitAll()
            customizer.anyRequest().authenticated()
        }.oauth2ResourceServer { oauth2ResourceServer ->
            oauth2ResourceServer.jwt(Customizer.withDefaults())
            oauth2ResourceServer.bearerTokenResolver(bearerTokenResolver)
        }.cors(Customizer.withDefaults())
            .csrf { it.disable() }
        return http.build()
    }
}
