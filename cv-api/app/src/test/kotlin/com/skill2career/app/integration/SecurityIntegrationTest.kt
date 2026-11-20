package com.skill2career.app.integration

import kotlin.test.Test

class SecurityIntegrationTest : KeycloakIntegrationBase() {
    @Test
    fun `refuses a request with no token`() {
        getAnonymous("/anything").expectStatus().isUnauthorized
    }

    @Test
    fun `refuses a token keycloak did not sign`() {
        getWithToken("/anything", token = "not-a-jwt").expectStatus().isUnauthorized
    }

    @Test
    fun `lets a keycloak user through, to a route that does not exist`() {
        get("/anything", username = CANDIDATE).expectStatus().isNotFound
    }

    @Test
    fun `secures every response with headers`() {
        getAnonymous("/health")
            .expectHeader()
            .valueEquals("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")
            .expectHeader()
            .valueEquals("X-Content-Type-Options", "nosniff")
            .expectHeader()
            .valueEquals("Referrer-Policy", "no-referrer")
    }
}
