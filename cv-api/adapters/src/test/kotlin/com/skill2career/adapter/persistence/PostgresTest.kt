package com.skill2career.adapter.persistence

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.postgresql.PostgreSQLContainer

/** The jpa adapters, against real postgres with the real flyway migrations, never an in memory stand in. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
abstract class PostgresTest {
    companion object {
        private val postgres: PostgreSQLContainer by lazy {
            PostgreSQLContainer("postgres:16").apply {
                withDatabaseName("skill2career")
                withUsername("skill2career")
                withPassword("skill2career")
                start()
            }
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.flyway.default-schema") { "skill2career" }
            registry.add("spring.jpa.properties.hibernate.default_schema") { "skill2career" }
        }
    }
}
