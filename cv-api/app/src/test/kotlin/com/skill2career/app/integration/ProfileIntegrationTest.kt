package com.skill2career.app.integration

import kotlin.test.Test

/** The whole journey, through keycloak's real tokens and postgres. */
class ProfileIntegrationTest : KeycloakIntegrationBase() {
    private val profile =
        mapOf(
            "name" to "Casey Candidate",
            "skills" to listOf("Kotlin", "kotlin", "SQL"),
            "experience" to "Five years building payment APIs.",
            "education" to "BSc Computer Science",
            "email" to "casey@example.com",
            "workHistory" to listOf(mapOf("company" to "Acme", "title" to "Engineer", "bullets" to listOf("Shipped it"))),
        )

    @Test
    fun `a candidate saves their profile and reads it back`() {
        put("/profile", profile, username = CANDIDATE).expectStatus().isOk

        get("/profile", username = CANDIDATE)
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.name")
            .isEqualTo("Casey Candidate")
            .jsonPath("$.skills.length()")
            .isEqualTo(2)
            .jsonPath("$.workHistory[0].company")
            .isEqualTo("Acme")
    }

    @Test
    fun `one candidate never sees another's profile`() {
        put("/profile", profile, username = CANDIDATE).expectStatus().isOk

        get("/profile", username = CANDIDATE_OTHER).expectStatus().isNotFound
    }
}
