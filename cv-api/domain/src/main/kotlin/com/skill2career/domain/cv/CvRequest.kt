package com.skill2career.domain.cv

import com.skill2career.domain.profile.Fact
import com.skill2career.domain.profile.OwnerId

enum class CvStatus { REQUESTED, AWAITING_ANSWERS, WRITING, COMPLETED, FAILED }

data class CvRequest(
    val id: Long? = null,
    val ownerId: OwnerId,
    val status: CvStatus,
    val questions: List<Question> = emptyList(),
    val answers: List<Answer> = emptyList(),
    val rounds: List<Round> = emptyList(),
    val cv: Cv? = null,
    val failure: String? = null,
    /** The job this CV is aimed at; null for a general CV, aimed at the profile's target role. */
    val jobId: Long? = null,
) {
    /** The interviewer asked these. None means the profile already says enough. */
    fun asked(questions: List<Question>): CvRequest {
        check(status == CvStatus.REQUESTED) { "only a new request is interviewed, this one is $status" }
        require(questions.size <= MAX_QUESTIONS) { "at most $MAX_QUESTIONS questions" }
        require(questions.map { it.id }.toSet().size == questions.size) { "each question is asked once" }
        return copy(
            questions = questions,
            status = if (questions.isEmpty()) CvStatus.WRITING else CvStatus.AWAITING_ANSWERS,
        )
    }

    /** The candidate's answers. A question they left out counts as skipped. */
    fun answered(given: List<Answer>): CvRequest {
        check(status == CvStatus.AWAITING_ANSWERS) { "only a request waiting for answers is answered, this one is $status" }
        val asked = questions.map { it.id }.toSet()
        require(given.all { it.questionId in asked }) { "an answer must be to a question that was asked" }
        val byQuestion = given.associateBy { it.questionId }
        return copy(
            answers = questions.map { byQuestion[it.id] ?: Answer(it.id, null) },
            status = CvStatus.WRITING,
        )
    }

    /** The answers given, not skipped, as facts for the profile, in the words of their questions. */
    fun facts(): List<Fact> =
        answers
            .filterNot { it.skipped }
            .map { answer -> Fact(questions.first { it.id == answer.questionId }.text, answer.text!!) }

    /** Until the critic approves a draft, or the rounds run out. */
    val needsAnotherRound: Boolean
        get() = rounds.isEmpty() || (!rounds.last().review.approved && rounds.size < MAX_ROUNDS)

    fun withRound(round: Round): CvRequest {
        check(status == CvStatus.WRITING) { "only a request being written takes rounds, this one is $status" }
        require(round.number == rounds.size + 1) { "round ${round.number} is out of order" }
        require(round.number <= MAX_ROUNDS) { "at most $MAX_ROUNDS rounds" }
        return copy(rounds = rounds + round)
    }

    /** Finished, with the approved draft, or else the best scoring one. */
    fun completed(): CvRequest {
        check(status == CvStatus.WRITING && !needsAnotherRound) { "the writing is not finished" }
        return copy(status = CvStatus.COMPLETED, cv = best().draft)
    }

    fun failed(reason: String): CvRequest {
        check(status != CvStatus.COMPLETED && status != CvStatus.FAILED) { "the request has already finished" }
        return copy(status = CvStatus.FAILED, failure = reason)
    }

    // On a tied score the later draft wins, as it was written with more feedback.
    private fun best(): Round =
        rounds.firstOrNull { it.review.approved }
            ?: rounds.maxWith(compareBy<Round> { it.review.score }.thenBy { it.number })

    companion object {
        const val MAX_QUESTIONS = 5
        const val MAX_ROUNDS = 3

        fun requested(
            ownerId: OwnerId,
            jobId: Long? = null,
        ) = CvRequest(ownerId = ownerId, status = CvStatus.REQUESTED, jobId = jobId)
    }
}
