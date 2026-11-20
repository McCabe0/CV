package com.skill2career.app.wiring

import com.skill2career.adapter.persistence.profile.ProfileJpaAdapter
import com.skill2career.adapter.web.security.SecurityConfiguration
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/** What every role needs: the database, and security, as every role serves its health checks. */
@Configuration(proxyBeanMethods = false)
@EntityScan("com.skill2career.adapter.persistence")
@EnableJpaRepositories("com.skill2career.adapter.persistence")
@Import(SecurityConfiguration::class, ProfileJpaAdapter::class)
class CommonWiring
