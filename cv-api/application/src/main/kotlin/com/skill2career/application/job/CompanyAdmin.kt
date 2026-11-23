package com.skill2career.application.job

import com.skill2career.domain.job.CrawlResult
import com.skill2career.domain.job.Jobs
import com.skill2career.domain.job.SourceType
import com.skill2career.domain.job.TrackedCompany
import java.time.Clock

// What an admin does with the tracked companies, in the admin zone. See decisions/jobs.md.

/** A company as the admin zone shows it: its live jobs, and how its last crawl went, if it has had one. */
data class CompanyHealth(
    val company: TrackedCompany,
    val liveJobs: Int,
    val lastCrawl: CrawlResult?,
)

/** Every company, tracked or not, with its health: the tracked ones first, each group by name. */
class ListCompanies(
    private val companies: TrackedCompanyStore,
    private val jobs: JobStore,
    private val results: CrawlResults,
) {
    fun list(): List<CompanyHealth> {
        val live = jobs.liveCounts()
        val latest = results.latest()
        return companies
            .all()
            .sortedWith(compareBy<TrackedCompany> { !it.tracked }.thenBy { it.name.lowercase() })
            .map { CompanyHealth(it, live[it.id] ?: 0, latest[it.id]) }
    }
}

/**
 * Tracks a company's board, once it answers, so a wrong token is refused rather than failing every day,
 * and queues its first crawl at once. A board tracked before, then stopped, is tracked again under the
 * name given now, so its jobs and history stay with it. Saving and queueing belong in one transaction.
 */
class TrackCompany(
    private val companies: TrackedCompanyStore,
    sources: List<JobSource>,
    private val queue: CrawlQueue,
) {
    private val sources = sources.associateBy { it.type }

    fun track(
        name: String,
        source: SourceType,
        boardToken: String,
    ): TrackedCompany {
        val known = companies.findByBoard(source, boardToken)
        if (known != null && known.tracked) throw CompanyAlreadyTracked(source, boardToken)
        val company = known?.trackedAgain(name) ?: TrackedCompany(name = name, source = source, boardToken = boardToken)
        val board = this.sources[source] ?: throw NoJobSource(source)
        board.fetch(company)
        return companies.save(company).also(queue::queue)
    }
}

/**
 * Stops tracking a company: it is no longer crawled, and its live jobs are closed at once, so nobody is
 * matched to them. The company and its jobs are kept, so CVs and summaries for them still have them.
 */
class StopTracking(
    private val companies: TrackedCompanyStore,
    private val jobs: JobStore,
    private val clock: Clock,
) {
    fun stop(id: Long): TrackedCompany {
        val company = companies.find(id) ?: throw TrackedCompanyNotFound(id)
        if (!company.tracked) return company
        val now = clock.instant()
        val closed = Jobs.close(jobs.forCompany(id), now)
        if (closed.isNotEmpty()) jobs.saveAll(closed)
        return companies.save(company.untracked(now))
    }
}

/** Queues one tracked company's crawl now, rather than waiting for the next day's. */
class CrawlNow(
    private val companies: TrackedCompanyStore,
    private val queue: CrawlQueue,
) {
    fun crawl(id: Long) {
        val company = companies.find(id) ?: throw TrackedCompanyNotFound(id)
        if (!company.tracked) throw CompanyNotTracked(id)
        queue.queue(company)
    }
}
