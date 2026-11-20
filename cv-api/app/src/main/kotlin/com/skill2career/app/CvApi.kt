package com.skill2career.app

import com.skill2career.app.role.AppRoles
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment

/**
 * Scans only :app. Each adapter is wired in by name in [com.skill2career.app.wiring], so what a role
 * runs is written down, not found by scanning.
 */
@SpringBootApplication
class CvApi {
    /** Read at start, so a misspelt `app.roles` fails here, whether or not anything asks for a role. */
    @Bean
    fun appRoles(environment: Environment): AppRoles = AppRoles.parse(environment.getRequiredProperty(AppRoles.PROPERTY))
}

fun main(args: Array<String>) {
    runApplication<CvApi>(*args)
}
