package com.skill2career.application.job

import com.skill2career.domain.job.Jobs
import java.time.Clock

/** The daily crawl: one queued crawl per company, so the work spreads over the job-workers. */
class ScheduleCrawl(
    private val companies: TrackedCompanyStore,
    private val queue: CrawlQueue,
) {
    fun schedule() = companies.all().forEach(queue::queue)
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
