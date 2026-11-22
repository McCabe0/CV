package com.skill2career.domain.job

import java.time.Instant

/** The applicant tracking systems whose public job boards we read. */
enum class SourceType { GREENHOUSE, LEVER, ASHBY, WORKABLE }

/** A company whose job board is crawled: which system it uses, and its name on that system. */
data class TrackedCompany(
    val id: Long? = null,
    val name: String,
    val source: SourceType,
    val boardToken: String,
) {
    init {
        require(name.isNotBlank()) { "a company needs a name" }
        require(boardToken.isNotBlank()) { "a company needs its board token" }
    }
}

/** A job as its board lists it. The apply link is the company's own page. */
data class JobPosting(
    val externalId: String,
    val title: String,
    val location: String?,
    val description: String,
    val applyUrl: String,
) {
    init {
        require(externalId.isNotBlank()) { "a posting needs the board's id" }
        require(title.isNotBlank()) { "a posting needs a title" }
        require(applyUrl.isNotBlank()) { "a posting needs a link to apply" }
    }
}

/** Our copy of a posting. Once its board stops listing it, it is inactive and never matched. */
data class Job(
    val id: Long? = null,
    val companyId: Long,
    val source: SourceType,
    val posting: JobPosting,
    val firstSeenAt: Instant,
    val inactiveSince: Instant? = null,
) {
    val active: Boolean get() = inactiveSince == null
}

object Jobs {
    /**
     * What a crawl of [company]'s board changes: the jobs to save so our copy matches what the board
     * [listed] [at] that moment. New postings are added, changed ones updated, and those no longer listed
     * marked inactive. A job listed again is active again. Unchanged jobs are left out, so nothing is
     * saved for them.
     */
    fun reconcile(
        company: TrackedCompany,
        stored: List<Job>,
        listed: List<JobPosting>,
        at: Instant,
    ): List<Job> {
        val companyId = requireNotNull(company.id) { "only a saved company is crawled" }
        val postings = listed.distinctBy { it.externalId }
        val byExternalId = stored.associateBy { it.posting.externalId }

        val current =
            postings.map { posting ->
                byExternalId[posting.externalId]?.copy(posting = posting, inactiveSince = null)
                    ?: Job(companyId = companyId, source = company.source, posting = posting, firstSeenAt = at)
            }
        val gone =
            stored
                .filter { job -> job.active && postings.none { it.externalId == job.posting.externalId } }
                .map { it.copy(inactiveSince = at) }

        val unchanged = stored.toSet()
        return (current + gone).filter { it !in unchanged }
    }
}
