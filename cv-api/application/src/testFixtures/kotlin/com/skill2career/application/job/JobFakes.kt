package com.skill2career.application.job

import com.skill2career.domain.job.Job
import com.skill2career.domain.job.JobPosting
import com.skill2career.domain.job.SourceType
import com.skill2career.domain.job.TrackedCompany
import java.time.LocalDate

// Hand written fakes of the job ports, for any module's tests.

class InMemoryTrackedCompanyStore(
    vararg companies: TrackedCompany,
) : TrackedCompanyStore {
    private val companies = companies.associateBy { it.id!! }.toMutableMap()
    private var nextId = (this.companies.keys.maxOrNull() ?: 0) + 1

    override fun all(): List<TrackedCompany> = companies.values.sortedBy { it.id }

    override fun find(id: Long): TrackedCompany? = companies[id]

    fun save(company: TrackedCompany): TrackedCompany {
        val saved = if (company.id == null) company.copy(id = nextId++) else company
        companies[saved.id!!] = saved
        return saved
    }
}

class InMemoryJobStore : JobStore {
    private val jobs = mutableMapOf<Long, Job>()
    private var nextId = 1L

    /** What the last [saveAll] was given. */
    var lastSaved: List<Job> = emptyList()
        private set

    override fun forCompany(companyId: Long): List<Job> = jobs.values.filter { it.companyId == companyId }.sortedBy { it.id }

    override fun saveAll(jobs: List<Job>) {
        lastSaved = jobs
        jobs.forEach { job ->
            val saved = if (job.id == null) job.copy(id = nextId++) else job
            this.jobs[saved.id!!] = saved
        }
    }
}

class FakeJobSource(
    override val type: SourceType,
) : JobSource {
    private val boards = mutableMapOf<String, List<JobPosting>>()
    private var failing = false
    val fetched = mutableListOf<TrackedCompany>()

    fun lists(
        company: TrackedCompany,
        vararg postings: JobPosting,
    ) {
        boards[company.boardToken] = postings.toList()
    }

    fun fails() {
        failing = true
    }

    override fun fetch(company: TrackedCompany): List<JobPosting> {
        fetched += company
        check(!failing) { "the board is down" }
        return boards[company.boardToken].orEmpty()
    }
}

class RecordingCrawlQueue : CrawlQueue {
    val queued = mutableListOf<Long>()

    override fun queue(company: TrackedCompany) {
        queued += company.id!!
    }
}

class InMemoryCrawlRuns : CrawlRuns {
    private val claimed = mutableSetOf<LocalDate>()

    override fun claim(day: LocalDate): Boolean = claimed.add(day)
}
