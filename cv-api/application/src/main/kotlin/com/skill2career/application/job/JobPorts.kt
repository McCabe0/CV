package com.skill2career.application.job

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

/** The companies whose boards are crawled. The admin zone keeps the list. */
interface TrackedCompanyStore {
    fun all(): List<TrackedCompany>

    fun find(id: Long): TrackedCompany?
}

/** Our copy of every board's jobs. */
interface JobStore {
    fun forCompany(companyId: Long): List<Job>

    /** Saves new and changed jobs together: all of them, or none. */
    fun saveAll(jobs: List<Job>)
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

class NoJobSource(
    type: SourceType,
) : RuntimeException("no job source reads $type boards")
