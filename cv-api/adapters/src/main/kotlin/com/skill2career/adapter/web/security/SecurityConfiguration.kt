package com.skill2career.adapter.web.security

import jakarta.servlet.DispatcherType
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.HeadersDsl
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter
import org.springframework.security.web.util.matcher.DispatcherTypeRequestMatcher

/**
 * Default deny, with keycloak's tokens. Roles come from the token's `roles` claim, as `ROLE_<name>`.
 * The start fails if no JwtDecoder can be built, ie the issuer is not set.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfiguration {
    private companion object {
        val DOCS = arrayOf("/webjars/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")

        /** The api only returns data, so nothing may run in, load into or frame a response. */
        const val API_POLICY = "default-src 'none'; frame-ancestors 'none'"
        const val DOCS_POLICY =
            "default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; " +
                "connect-src 'self' http://localhost:8180; object-src 'none'; base-uri 'self'; form-action 'self'; " +
                "frame-ancestors 'none'"
    }

    private fun HeadersDsl.secure(
        contentPolicy: String,
        openerPolicy: CrossOriginOpenerPolicy = CrossOriginOpenerPolicy.SAME_ORIGIN,
    ) {
        contentSecurityPolicy { policyDirectives = contentPolicy }
        xssProtection { headerValue = XXssProtectionHeaderWriter.HeaderValue.DISABLED }
        referrerPolicy { policy = ReferrerPolicy.NO_REFERRER }
        permissionsPolicy { policy = "camera=(), microphone=(), geolocation=(), payment=(), usb=()" }
        crossOriginResourcePolicy { policy = CrossOriginResourcePolicy.SAME_ORIGIN }
        crossOriginOpenerPolicy { policy = openerPolicy }
    }

    /** Swagger signs in with keycloak in a popup, so its pages are not cross origin isolated. */
    @Bean
    @Order(1)
    fun docsFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            securityMatcher(*DOCS)
            headers { secure(DOCS_POLICY, CrossOriginOpenerPolicy.UNSAFE_NONE) }
            authorizeHttpRequests { authorize(anyRequest, permitAll) }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            csrf { disable() }
        }
        return http.build()
    }

    @Bean
    @Order(2)
    fun apiFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            headers { secure(API_POLICY) }
            authorizeHttpRequests {
                authorize("/health/**", permitAll)
                authorize("/info", permitAll)
                authorize(DispatcherTypeRequestMatcher(DispatcherType.ERROR), permitAll)
                authorize(anyRequest, authenticated)
            }
            oauth2ResourceServer { jwt { } }
            // Every request carries its token, so the api keeps no sessions.
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            csrf { disable() }
        }
        return http.build()
    }
}
