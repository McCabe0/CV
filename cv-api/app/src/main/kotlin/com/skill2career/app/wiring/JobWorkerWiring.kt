package com.skill2career.app.wiring

import com.skill2career.adapter.jobboard.AshbySource
import com.skill2career.adapter.jobboard.GreenhouseSource
import com.skill2career.adapter.jobboard.JobBoardSettings
import com.skill2career.adapter.jobboard.LeverSource
import com.skill2career.adapter.jobboard.WorkableSource
import com.skill2career.adapter.messaging.CrawlScheduler
import com.skill2career.adapter.messaging.JobCrawlListener
import com.skill2career.adapter.messaging.OutboxCrawlQueue
import com.skill2career.adapter.messaging.PostgresCrawlRuns
import com.skill2career.app.role.AppRole
import com.skill2career.app.role.ConditionalOnRole
import com.skill2career.application.job.CrawlCompany
import com.skill2career.application.job.CrawlQueue
import com.skill2career.application.job.CrawlResults
import com.skill2career.application.job.CrawlRuns
import com.skill2career.application.job.JobSource
import com.skill2career.application.job.JobStore
import com.skill2career.application.job.RecordCrawlFailure
import com.skill2career.application.job.ScheduleCrawl
import com.skill2career.application.job.TrackedCompanyStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock

/**
 * The job-worker: asks for the day's crawl each hour, and crawls each queued company's board. The boards'
 * urls can be changed, so the tests and the local e2e stack can play them. See decisions/jobs.md.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnRole(AppRole.JOB_WORKER)
@Import(JobCrawlListener::class, CrawlScheduler::class, OutboxCrawlQueue::class, PostgresCrawlRuns::class)
class JobWorkerWiring {
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

    @Bean
    fun crawlCompany(
        companies: TrackedCompanyStore,
        sources: List<JobSource>,
        jobs: JobStore,
        results: CrawlResults,
        clock: Clock,
    ): CrawlCompany = CrawlCompany(companies, sources, jobs, results, clock)

    @Bean
    fun recordCrawlFailure(
        results: CrawlResults,
        clock: Clock,
    ): RecordCrawlFailure = RecordCrawlFailure(results, clock)

    @Bean
    fun scheduleCrawl(
        companies: TrackedCompanyStore,
        runs: CrawlRuns,
        queue: CrawlQueue,
    ): ScheduleCrawl = ScheduleCrawl(companies, runs, queue)

    @Bean
    fun crawlSchedule(scheduler: CrawlScheduler): CrawlSchedule = CrawlSchedule(scheduler)
}

/**
 * Asks for the day's crawl soon after the start, then every hour. Only the first ask each day, from any
 * job-worker, queues it, so a worker that was down still crawls that day, and none crawls it twice.
 */
class CrawlSchedule(
    private val scheduler: CrawlScheduler,
) {
    @Scheduled(
        initialDelayString = "\${jobs.crawl.first-check-ms:60000}",
        fixedDelayString = "\${jobs.crawl.check-every-ms:3600000}",
    )
    fun check() = scheduler.run()
}
