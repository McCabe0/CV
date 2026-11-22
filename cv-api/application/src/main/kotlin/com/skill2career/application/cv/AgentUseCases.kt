package com.skill2career.application.cv

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

/** The writer and critic, in turn, until a draft is approved or the rounds run out. */
class WriteCv(
    private val requests: CvRequestStore,
    private val profiles: ProfileStore,
    private val writer: Writer,
    private val critic: Critic,
) {
    fun write(id: Long): CvRequest {
        var request = requests.find(id) ?: throw CvRequestNotFound(id)
        if (request.status != CvStatus.WRITING) return request

        val profile = profiles.findByOwner(request.ownerId) ?: return requests.save(request.failed(PROFILE_GONE))
        val facts =
            request.answers
                .filterNot { it.skipped }
                .map { answer -> Fact(request.questions.first { it.id == answer.questionId }.text, answer.text!!) }

        try {
            while (request.needsAnotherRound) {
                val brief = Brief(profile, facts, request.rounds.lastOrNull())
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

private const val PROFILE_GONE = "The profile was deleted"
