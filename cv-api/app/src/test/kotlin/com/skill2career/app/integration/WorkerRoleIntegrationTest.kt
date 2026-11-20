package com.skill2career.app.integration

import org.springframework.test.context.TestPropertySource
import kotlin.test.Test

/** A worker serves its health checks, for kubernetes, and none of the api. */
@TestPropertySource(properties = ["app.roles=cv-worker,job-worker"])
class WorkerRoleIntegrationTest : KeycloakIntegrationBase() {
    @Test
    fun `serves its health`() {
        getAnonymous("/health").expectStatus().isOk
    }

    @Test
    fun `does not serve the api, even to a signed in candidate`() {
        get("/profile", username = CANDIDATE).expectStatus().isNotFound
    }
}
