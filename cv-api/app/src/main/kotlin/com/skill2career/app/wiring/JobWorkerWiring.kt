package com.skill2career.app.wiring

import com.skill2career.adapter.messaging.CrawlScheduler
import com.skill2career.adapter.messaging.JobCrawlListener
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
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock

/**
 * The job-worker: asks for the day's crawl each hour, and crawls each queued company's board, from
 * [JobBoardWiring]. See decisions/jobs.md.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnRole(AppRole.JOB_WORKER)
@Import(JobBoardWiring::class, JobCrawlListener::class, CrawlScheduler::class, PostgresCrawlRuns::class)
class JobWorkerWiring {
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
