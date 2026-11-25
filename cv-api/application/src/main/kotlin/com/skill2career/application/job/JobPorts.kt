package com.skill2career.application.job

import com.skill2career.domain.job.CrawlResult
import com.skill2career.domain.job.Job
import com.skill2career.domain.job.JobFilter
import com.skill2career.domain.job.JobPosting
import com.skill2career.domain.job.ListedJob
import com.skill2career.domain.job.SourceType
import com.skill2career.domain.job.TrackedCompany
import java.time.LocalDate

// The driven ports the crawl and matching need. Adapters implement them.

/** Searches our copy of the jobs, never the boards. */
interface JobIndex {
    /**
     * Up to [limit] active jobs that [filter] accepts and that mention any of the [terms], best text
     * match first.
     */
    fun search(
        terms: List<String>,
        filter: JobFilter,
        limit: Int,
    ): List<ListedJob>
}

/** One applicant tracking system's public job board. Throws when the board cannot be read. */
interface JobSource {
    val type: SourceType

    fun fetch(company: TrackedCompany): List<JobPosting>
}

/** The companies whose boards are crawled, and those no longer tracked. The admin zone keeps the list. */
interface TrackedCompanyStore {
    fun all(): List<TrackedCompany>

    fun find(id: Long): TrackedCompany?

    /** The company with this board, tracked or not; null when there is none. */
    fun findByBoard(
        source: SourceType,
        boardToken: String,
    ): TrackedCompany?

    fun save(company: TrackedCompany): TrackedCompany
}

/** How each company's crawls went, for the admin zone. */
interface CrawlResults {
    fun record(result: CrawlResult)

    /** Each company's latest crawl, by company id. */
    fun latest(): Map<Long, CrawlResult>
}

/** Our copy of every board's jobs. */
interface JobStore {
    fun forCompany(companyId: Long): List<Job>

    /** The job, active or not; null when we never had it. */
    fun find(id: Long): Job?

    /** Saves new and changed jobs together: all of them, or none. */
    fun saveAll(jobs: List<Job>)

    /** How many active jobs each company has, by company id. A company with none is left out. */
    fun liveCounts(): Map<Long, Int>
}

/** Queues one company's crawl for whichever job-worker is free. */
interface CrawlQueue {
    fun queue(company: TrackedCompany)
}

/** Which days' crawls have been queued, so each day's is queued once, by whichever job-worker asks first. */
interface CrawlRuns {
    /** True for the one caller that claims [day]; false for every later one. */
    fun claim(day: LocalDate): Boolean
}

class TrackedCompanyNotFound(
    id: Long,
) : RuntimeException("no tracked company $id")

class CompanyAlreadyTracked(
    source: SourceType,
    boardToken: String,
) : RuntimeException("the $source board '$boardToken' is already tracked")

class CompanyNotTracked(
    id: Long,
) : RuntimeException("company $id is no longer tracked")

class NoJobSource(
    type: SourceType,
) : RuntimeException("no job source reads $type boards")
