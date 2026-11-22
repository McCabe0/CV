package com.skill2career.app.wiring

import com.skill2career.adapter.messaging.OutboxRelay
import com.skill2career.adapter.web.ApiExceptionHandler
import com.skill2career.adapter.web.profile.ProfileResource
import com.skill2career.app.role.AppRole
import com.skill2career.app.role.ConditionalOnRole
import com.skill2career.application.cv.AnswerQuestions
import com.skill2career.application.cv.CvRequestStore
import com.skill2career.application.cv.FindCvRequest
import com.skill2career.application.cv.RequestCv
import com.skill2career.application.profile.FindProfile
import com.skill2career.application.profile.ProfileStore
import com.skill2career.application.profile.SaveProfile
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled

/**
 * The http api. Only the `api` role serves it. The use cases are plain kotlin, made beans here. The api
 * writes the outbox, so it runs the relay that sends it to kafka.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnRole(AppRole.API)
@EnableScheduling
@Import(ProfileResource::class, ApiExceptionHandler::class, OutboxRelay::class)
class ApiWiring {
    @Bean
    fun saveProfile(store: ProfileStore): SaveProfile = SaveProfile(store)

    @Bean
    fun findProfile(store: ProfileStore): FindProfile = FindProfile(store)

    @Bean
    fun requestCv(
        profiles: ProfileStore,
        requests: CvRequestStore,
    ): RequestCv = RequestCv(profiles, requests)

    @Bean
    fun findCvRequest(requests: CvRequestStore): FindCvRequest = FindCvRequest(requests)

    @Bean
    fun answerQuestions(
        requests: CvRequestStore,
        profiles: ProfileStore,
    ): AnswerQuestions = AnswerQuestions(requests, profiles)

    @Bean
    fun outboxSchedule(relay: OutboxRelay): OutboxSchedule = OutboxSchedule(relay)
}

/** Sends the outbox every moment. Each instance runs one; the relay's row locks keep them apart. */
class OutboxSchedule(
    private val relay: OutboxRelay,
) {
    @Scheduled(fixedDelayString = "\${outbox.relay-delay-ms:500}")
    fun relay() {
        relay.relay()
    }
}
