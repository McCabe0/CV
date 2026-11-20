package com.skill2career.app.wiring

import com.skill2career.adapter.web.ApiExceptionHandler
import com.skill2career.adapter.web.profile.ProfileResource
import com.skill2career.app.role.AppRole
import com.skill2career.app.role.ConditionalOnRole
import com.skill2career.application.profile.FindProfile
import com.skill2career.application.profile.ProfileStore
import com.skill2career.application.profile.SaveProfile
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

/** The http api. Only the `api` role serves it. The use cases are plain kotlin, made beans here. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnRole(AppRole.API)
@Import(ProfileResource::class, ApiExceptionHandler::class)
class ApiWiring {
    @Bean
    fun saveProfile(store: ProfileStore): SaveProfile = SaveProfile(store)

    @Bean
    fun findProfile(store: ProfileStore): FindProfile = FindProfile(store)
}
