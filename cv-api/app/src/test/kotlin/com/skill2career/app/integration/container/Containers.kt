package com.skill2career.app.integration.container

import dasniko.testcontainers.keycloak.KeycloakContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Duration

/**
 * One of each container for the whole test run, started on first use. The same versions as
 * docker-compose.yml, and keycloak with the same realm, so what is tested is what runs.
 */
object Containers {
    const val REALM = "skill2career"

    val postgres: PostgreSQLContainer by lazy {
        PostgreSQLContainer("postgres:16").apply {
            withDatabaseName("skill2career")
            withUsername("skill2career")
            withPassword("skill2career")
            start()
        }
    }

    val keycloak: KeycloakContainer by lazy {
        KeycloakContainer("quay.io/keycloak/keycloak:26.0").apply {
            withRealmImportFile("realm-export.json")
            // Keycloak builds its own database on first start, which takes over 2 minutes on a slow machine.
            withStartupTimeout(Duration.ofMinutes(5))
            start()
        }
    }

    val issuer: String by lazy { "${keycloak.authServerUrl}/realms/$REALM" }
}
