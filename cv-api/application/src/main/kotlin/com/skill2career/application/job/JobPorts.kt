package com.skill2career.application.job

import com.skill2career.domain.job.Job
import com.skill2career.domain.job.JobPosting
import com.skill2career.domain.job.SourceType
import com.skill2career.domain.job.TrackedCompany

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

class TrackedCompanyNotFound(
    id: Long,
) : RuntimeException("no tracked company $id")

class NoJobSource(
    type: SourceType,
) : RuntimeException("no job source reads $type boards")
