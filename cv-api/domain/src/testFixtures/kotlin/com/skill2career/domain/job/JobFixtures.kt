package com.skill2career.domain.job

import java.time.Instant

/** Valid companies, postings and jobs, shared by every module's tests, each field changeable by name. */
object JobFixtures {
    val COMPANY = TrackedCompany(id = 1, name = "Monzo", source = SourceType.GREENHOUSE, boardToken = "monzo")

    val FIRST_SEEN: Instant = Instant.parse("2026-11-20T06:00:00Z")

    fun posting(
        externalId: String = "4001",
        title: String = "Backend Engineer",
        location: String? = "London",
        description: String = "Build payment APIs in Kotlin.",
        applyUrl: String = "https://monzo.com/careers/4001",
        workplace: Workplace? = null,
    ): JobPosting = JobPosting(externalId, title, location, description, applyUrl, workplace)

    fun job(
        id: Long? = null,
        company: TrackedCompany = COMPANY,
        posting: JobPosting = posting(),
        firstSeenAt: Instant = FIRST_SEEN,
        inactiveSince: Instant? = null,
    ): Job = Job(id, company.id!!, company.source, posting, firstSeenAt, inactiveSince)
}
