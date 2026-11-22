package com.skill2career.application.job

import com.skill2career.domain.job.Jobs
import java.time.Clock
import java.time.LocalDate

/**
 * The daily crawl: one queued crawl per company, so the work spreads over the job-workers. Every
 * job-worker asks; only the first to claim [today] queues it. The claim and the queue belong in one
 * transaction, so a failed queue leaves the day to claim again. Returns whether this call queued it.
 */
class ScheduleCrawl(
    private val companies: TrackedCompanyStore,
    private val runs: CrawlRuns,
    private val queue: CrawlQueue,
) {
    fun schedule(today: LocalDate): Boolean {
        if (!runs.claim(today)) return false
        companies.all().forEach(queue::queue)
        return true
    }
}

/**
 * Crawls one company's board, so our copy of its jobs matches what the board lists now. A board that
 * fails throws before anything is saved, so its jobs stay as they were, and the crawl can be retried.
 */
class CrawlCompany(
    private val companies: TrackedCompanyStore,
    sources: List<JobSource>,
    private val jobs: JobStore,
    private val clock: Clock,
) {
    private val sources = sources.associateBy { it.type }

    fun crawl(companyId: Long) {
        val company = companies.find(companyId) ?: throw TrackedCompanyNotFound(companyId)
        val source = sources[company.source] ?: throw NoJobSource(company.source)
        val listed = source.fetch(company)
        val changed = Jobs.reconcile(company, jobs.forCompany(companyId), listed, clock.instant())
        if (changed.isNotEmpty()) jobs.saveAll(changed)
    }
}
