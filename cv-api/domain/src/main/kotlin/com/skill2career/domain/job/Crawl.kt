package com.skill2career.domain.job

import java.time.Instant

/**
 * How a crawl of one board went. A failed crawl is tried again later that day; a parked one failed every
 * try, and waits for the next day's crawl.
 */
enum class CrawlOutcome { WORKED, FAILED, PARKED }

/** One crawl of a company's board, as the admin zone shows it: when, how it went, and what changed. */
data class CrawlResult(
    val companyId: Long,
    val at: Instant,
    val outcome: CrawlOutcome,
    val found: Int = 0,
    val added: Int = 0,
    val closed: Int = 0,
    val error: String? = null,
) {
    init {
        require(found >= 0 && added >= 0 && closed >= 0) { "a crawl counts jobs from zero" }
        require((outcome == CrawlOutcome.WORKED) == (error == null)) { "a crawl fails with a reason, and only then" }
        require(error == null || error.isNotBlank()) { "a failed crawl says why" }
    }

    companion object {
        /** The board listed [found] postings, and saving them [changed] these jobs. */
        fun worked(
            companyId: Long,
            at: Instant,
            found: Int,
            changed: List<Job>,
        ) = CrawlResult(
            companyId = companyId,
            at = at,
            outcome = CrawlOutcome.WORKED,
            found = found,
            added = changed.count { it.id == null },
            closed = changed.count { it.inactiveSince == at },
        )

        fun failed(
            companyId: Long,
            at: Instant,
            error: String,
        ) = CrawlResult(companyId, at, CrawlOutcome.FAILED, error = error)

        fun parked(
            companyId: Long,
            at: Instant,
            error: String,
        ) = CrawlResult(companyId, at, CrawlOutcome.PARKED, error = error)
    }
}
