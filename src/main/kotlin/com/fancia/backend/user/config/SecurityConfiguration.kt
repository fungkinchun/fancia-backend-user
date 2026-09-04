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
            if (path.startsWith("/api/webhooks/")) {
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
            customizer.requestMatchers(HttpMethod.POST, "/api/webhooks/**").permitAll()
            customizer.requestMatchers("/api/smart-match", "/api/smart-match/**").authenticated()
            customizer.requestMatchers("/api/chat", "/api/chat/**").authenticated()
            customizer.requestMatchers("/api/friends", "/api/friends/**").authenticated()
            customizer.requestMatchers(HttpMethod.GET, "/api/users/**").permitAll()
            customizer.requestMatchers(HttpMethod.POST, "/api/users").permitAll()
            customizer.requestMatchers(HttpMethod.PUT, "/internal/users/*/premium").permitAll()
            customizer.requestMatchers(HttpMethod.GET, "/internal/users/*/blocked").permitAll()
            customizer.requestMatchers(HttpMethod.GET, "/internal/users/*").permitAll()
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
