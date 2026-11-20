package com.skill2career.app.integration

import kotlin.test.Test

class HealthIntegrationTest : KeycloakIntegrationBase() {
    @Test
    fun `health is public and up, with the database`() {
        getAnonymous("/health")
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("UP")
    }

    @Test
    fun `liveness and readiness probes are public, for kubernetes`() {
        getAnonymous("/health/liveness").expectStatus().isOk
        getAnonymous("/health/readiness").expectStatus().isOk
    }
}
