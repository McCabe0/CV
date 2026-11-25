package com.skill2career.application.job

import com.skill2career.domain.job.CrawlResult
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
        companies.all().filter { it.tracked }.forEach(queue::queue)
        return true
    }
}

/**
 * Crawls one company's board, so our copy of its jobs matches what the board lists now, and records how
 * it went. A board that fails throws before anything is saved, so its jobs stay as they were, and the
 * crawl can be retried. A company untracked since its crawl was queued is skipped, so its jobs stay closed.
 */
class CrawlCompany(
    private val companies: TrackedCompanyStore,
    sources: List<JobSource>,
    private val jobs: JobStore,
    private val results: CrawlResults,
    private val clock: Clock,
) {
    private val sources = sources.associateBy { it.type }

    fun crawl(companyId: Long) {
        val company = companies.find(companyId) ?: throw TrackedCompanyNotFound(companyId)
        if (!company.tracked) return
        val source = sources[company.source] ?: throw NoJobSource(company.source)
        val listed = source.fetch(company)
        val now = clock.instant()
        val changed = Jobs.reconcile(company, jobs.forCompany(companyId), listed, now)
        if (changed.isNotEmpty()) jobs.saveAll(changed)
        results.record(CrawlResult.worked(companyId, now, listed.size, changed))
    }
}

/**
 * Records a crawl that failed, and why: failed while it will be tried again, parked after its [lastTry].
 * The crawl's own work is undone when it fails, so this is recorded on its own.
 */
class RecordCrawlFailure(
    private val results: CrawlResults,
    private val clock: Clock,
) {
    fun record(
        companyId: Long,
        error: String?,
        lastTry: Boolean,
    ) {
        val reason = error?.takeIf { it.isNotBlank() } ?: "no reason given"
        val at = clock.instant()
        results.record(if (lastTry) CrawlResult.parked(companyId, at, reason) else CrawlResult.failed(companyId, at, reason))
    }
}
