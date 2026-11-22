package com.skill2career.application.job

import com.skill2career.domain.job.Job
import com.skill2career.domain.job.JobPosting
import com.skill2career.domain.job.SourceType
import com.skill2career.domain.job.TrackedCompany
import java.time.LocalDate

// The driven ports the crawl needs. Adapters implement them.

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
