package com.skill2career.application.cv

import com.skill2career.application.job.ListedJobs
import com.skill2career.application.profile.ProfileStore
import com.skill2career.domain.cv.CvRequest
import com.skill2career.domain.cv.CvStatus
import com.skill2career.domain.cv.Round

// What the cv-worker does, for each message. Each is safe to run twice for the same request, as a
// message can be delivered more than once: it does nothing, or carries on from where it got to.

class InterviewCandidate(
    private val requests: CvRequestStore,
    private val profiles: ProfileStore,
    private val interviewer: Interviewer,
) {
    fun interview(id: Long): CvRequest {
        val request = requests.find(id) ?: throw CvRequestNotFound(id)
        if (request.status != CvStatus.REQUESTED) return request

        val profile = profiles.findByOwner(request.ownerId) ?: return requests.save(request.failed(PROFILE_GONE))
        return try {
            val questions = interviewer.questionsFor(profile).take(CvRequest.MAX_QUESTIONS)
            requests.save(request.asked(questions))
        } catch (failure: AgentFailure) {
            requests.save(request.failed("The interviewer failed: ${failure.message}"))
        }
    }
}

/**
 * The writer and critic, in turn, until a draft is approved or the rounds run out. A CV for a job is
 * aimed at it even if the job stopped being listed meanwhile: the candidate asked for it.
 */
class WriteCv(
    private val requests: CvRequestStore,
    private val profiles: ProfileStore,
    private val writer: Writer,
    private val critic: Critic,
    private val jobs: ListedJobs,
) {
    fun write(id: Long): CvRequest {
        var request = requests.find(id) ?: throw CvRequestNotFound(id)
        if (request.status != CvStatus.WRITING) return request

        val profile = profiles.findByOwner(request.ownerId) ?: return requests.save(request.failed(PROFILE_GONE))
        val job = request.jobId?.let(jobs::find)
        try {
            while (request.needsAnotherRound) {
                val brief = Brief(profile, request.rounds.lastOrNull(), job)
                val draft = writer.write(brief)
                val review = critic.review(brief, draft)
                // Saved each round, so the candidate sees progress, and a restart carries on from here.
                request = requests.save(request.withRound(Round(request.rounds.size + 1, draft, review)))
            }
        } catch (failure: AgentFailure) {
            return requests.save(request.failed("The writer or critic failed: ${failure.message}"))
        }
        return requests.save(request.completed())
    }
}

/**
 * Takes a request as far as it can go without the candidate: the interview, then, if nothing needs
 * asking or it has been answered, the writing. What the cv-worker does for each queued request.
 */
class AdvanceCvRequest(
    private val interview: InterviewCandidate,
    private val write: WriteCv,
) {
    fun advance(id: Long): CvRequest {
        val request = interview.interview(id)
        return if (request.status == CvStatus.WRITING) write.write(id) else request
    }
}

/**
 * The work could not be done, after every retry, eg the LLM was overloaded throughout. Rather than leave
 * the candidate waiting on a request that will never move, it fails, with a reason they can act on: they
 * ask again later. The rounds already written are kept. See decisions/messaging.md.
 */
class GiveUpOnCvRequest(
    private val requests: CvRequestStore,
) {
    fun giveUp(id: Long): CvRequest? {
        val request = requests.find(id) ?: return null
        if (request.status == CvStatus.COMPLETED || request.status == CvStatus.FAILED) return request
        return requests.save(request.failed(BUSY))
    }

    private companion object {
        const val BUSY = "Our writing service is busy right now. Please try again in a few minutes."
    }
}

private const val PROFILE_GONE = "The profile was deleted"
