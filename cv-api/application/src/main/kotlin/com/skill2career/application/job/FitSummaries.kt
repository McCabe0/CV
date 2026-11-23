package com.skill2career.application.job

import com.skill2career.application.cv.AgentFailure
import com.skill2career.application.profile.ProfileNotFound
import com.skill2career.application.profile.ProfileStore
import com.skill2career.domain.job.FitStatus
import com.skill2career.domain.job.FitSummary
import com.skill2career.domain.job.Job
import com.skill2career.domain.job.ListedJob
import com.skill2career.domain.profile.OwnerId
import com.skill2career.domain.profile.Profile

// Why a job fits a candidate: asked for on the api, written on the cv-worker, which has the LLM. See
// decisions/jobs.md.

/** An agent, as a driven port: says, in a few sentences, why [job] fits the candidate whose [profile] it is. */
fun interface FitExplainer {
    fun explain(
        profile: Profile,
        job: ListedJob,
    ): String
}

/** Where summaries are kept: one per candidate and job. A driven port: an adapter implements it. */
interface FitSummaryStore {
    /**
     * Saves the summary. With [queueWork], it is also queued for the cv-worker, in the same save: both
     * happen, or neither.
     */
    fun save(
        summary: FitSummary,
        queueWork: Boolean = false,
    ): FitSummary

    fun find(id: Long): FitSummary?

    fun find(
        ownerId: OwnerId,
        jobId: Long,
    ): FitSummary?
}

class JobNotFound(
    id: Long,
) : RuntimeException("no job $id")

class FitSummaryNotFound(
    what: String,
) : RuntimeException("no summary $what")

/**
 * A candidate asks why a listed job fits them. The first ask queues it to be written; later asks give
 * the same summary, so it is written once. A failed one is written afresh.
 */
class RequestFitSummary(
    private val profiles: ProfileStore,
    private val jobs: JobStore,
    private val summaries: FitSummaryStore,
) {
    fun request(
        ownerId: OwnerId,
        jobId: Long,
    ): FitSummary {
        profiles.findByOwner(ownerId) ?: throw ProfileNotFound(ownerId)
        jobs.find(jobId)?.takeIf(Job::active) ?: throw JobNotFound(jobId)

        val existing = summaries.find(ownerId, jobId)
        return when {
            existing == null -> summaries.save(FitSummary(ownerId = ownerId, jobId = jobId), queueWork = true)
            existing.status == FitStatus.FAILED -> summaries.save(existing.askedAgain(), queueWork = true)
            else -> existing
        }
    }
}

/** A candidate's own summary of a job. Another's is not found, so its existence is not given away. */
class FindFitSummary(
    private val summaries: FitSummaryStore,
) {
    fun find(
        ownerId: OwnerId,
        jobId: Long,
    ): FitSummary = summaries.find(ownerId, jobId) ?: throw FitSummaryNotFound("of job $jobId")
}

/**
 * What the cv-worker does for each queued summary. Safe to run twice, as a message can come twice: a
 * summary no longer being written is left as it is. An agent failure fails it, as asking the LLM again
 * will not help; any other error is let through, so the message is retried.
 */
class WriteFitSummary(
    private val summaries: FitSummaryStore,
    private val profiles: ProfileStore,
    private val jobs: JobStore,
    private val companies: TrackedCompanyStore,
    private val explainer: FitExplainer,
) {
    fun write(id: Long): FitSummary {
        val summary = summaries.find(id) ?: throw FitSummaryNotFound("$id")
        if (summary.status != FitStatus.WRITING) return summary

        val profile = profiles.findByOwner(summary.ownerId) ?: return summaries.save(summary.failed(PROFILE_GONE))
        val job = jobs.find(summary.jobId)?.takeIf(Job::active) ?: return summaries.save(summary.failed(JOB_GONE))
        val company = companies.find(job.companyId) ?: return summaries.save(summary.failed(JOB_GONE))

        return try {
            summaries.save(summary.written(explainer.explain(profile, ListedJob(job, company.name))))
        } catch (failure: AgentFailure) {
            summaries.save(summary.failed("The summary could not be written: ${failure.message}"))
        }
    }

    private companion object {
        const val PROFILE_GONE = "Your profile was deleted before this could be written."
        const val JOB_GONE = "The job is no longer listed."
    }
}

/**
 * The summary could not be written after every retry, eg the LLM was busy throughout. It fails, so the
 * candidate is not left waiting, and can ask again later. One already written is left as it is.
 */
class GiveUpOnFitSummary(
    private val summaries: FitSummaryStore,
) {
    fun giveUp(id: Long): FitSummary? {
        val summary = summaries.find(id) ?: return null
        if (summary.status != FitStatus.WRITING) return summary
        return summaries.save(summary.failed(BUSY))
    }

    private companion object {
        const val BUSY = "The AI was too busy to write this. Please ask again later."
    }
}
