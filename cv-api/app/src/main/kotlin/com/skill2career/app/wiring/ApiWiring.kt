package com.skill2career.app.wiring

import com.skill2career.adapter.messaging.CvProgressListener
import com.skill2career.adapter.persistence.job.PostgresJobIndex
import com.skill2career.adapter.web.ApiExceptionHandler
import com.skill2career.adapter.web.cv.CvRequestResource
import com.skill2career.adapter.web.cv.ProgressStreams
import com.skill2career.adapter.web.job.JobMatchResource
import com.skill2career.adapter.web.profile.ProfileResource
import com.skill2career.app.role.AppRole
import com.skill2career.app.role.ConditionalOnRole
import com.skill2career.application.cv.AnswerQuestions
import com.skill2career.application.cv.CvRequestStore
import com.skill2career.application.cv.FindCvRequest
import com.skill2career.application.cv.RequestCv
import com.skill2career.application.job.JobIndex
import com.skill2career.application.job.MatchJobs
import com.skill2career.application.profile.FindProfile
import com.skill2career.application.profile.ProfileStore
import com.skill2career.application.profile.SaveProfile
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

/**
 * The http api. Only the `api` role serves it. The use cases are plain kotlin, made beans here. Each
 * instance holds the progress streams of the candidates connected to it, fed from kafka.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnRole(AppRole.API)
@Import(
    ProfileResource::class,
    CvRequestResource::class,
    ProgressStreams::class,
    CvProgressListener::class,
    JobMatchResource::class,
    PostgresJobIndex::class,
    ApiExceptionHandler::class,
)
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
    fun matchJobs(
        profiles: ProfileStore,
        index: JobIndex,
    ): MatchJobs = MatchJobs(profiles, index)
}
