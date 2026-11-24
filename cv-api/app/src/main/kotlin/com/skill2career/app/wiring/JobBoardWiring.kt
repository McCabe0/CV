package com.skill2career.app.wiring

import com.skill2career.adapter.jobboard.AshbySource
import com.skill2career.adapter.jobboard.GreenhouseSource
import com.skill2career.adapter.jobboard.JobBoardSettings
import com.skill2career.adapter.jobboard.LeverSource
import com.skill2career.adapter.jobboard.WorkableSource
import com.skill2career.adapter.messaging.OutboxCrawlQueue
import com.skill2career.application.job.JobSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.time.Clock

/**
 * The job boards, and the queue of crawls: the job-worker crawls the boards, and the api reads one when
 * an admin adds a company, then queues its crawl. The boards' urls can be changed, so the tests and the
 * local e2e stack can play them. See decisions/jobs.md.
 */
@Configuration(proxyBeanMethods = false)
@Import(OutboxCrawlQueue::class)
class JobBoardWiring {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun greenhouse(
        @Value("\${jobs.boards.greenhouse-url:${GreenhouseSource.BOARDS}}") url: String,
    ): JobSource = GreenhouseSource(JobBoardSettings(url))

    @Bean
    fun lever(
        @Value("\${jobs.boards.lever-url:${LeverSource.BOARDS}}") url: String,
    ): JobSource = LeverSource(JobBoardSettings(url))

    @Bean
    fun ashby(
        @Value("\${jobs.boards.ashby-url:${AshbySource.BOARDS}}") url: String,
    ): JobSource = AshbySource(JobBoardSettings(url))

    @Bean
    fun workable(
        @Value("\${jobs.boards.workable-url:${WorkableSource.BOARDS}}") url: String,
    ): JobSource = WorkableSource(JobBoardSettings(url))
}
