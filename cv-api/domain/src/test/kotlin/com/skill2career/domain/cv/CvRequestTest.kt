package com.skill2career.domain.cv

import com.skill2career.domain.cv.CvFixtures.awaitingAnswers
import com.skill2career.domain.cv.CvFixtures.cv
import com.skill2career.domain.cv.CvFixtures.question
import com.skill2career.domain.cv.CvFixtures.rejected
import com.skill2career.domain.cv.CvFixtures.review
import com.skill2career.domain.cv.CvFixtures.writing
import com.skill2career.domain.profile.ProfileFixtures.OWNER
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import kotlin.test.Test

class CvRequestTest {
    @Test
    fun `starts requested, for its owner`() {
        val request = CvRequest.requested(OWNER)

        assertThat(request.status).isEqualTo(CvStatus.REQUESTED)
        assertThat(request.ownerId).isEqualTo(OWNER)
    }

    @Test
    fun `waits for answers once the interviewer has questions`() {
        val request = CvRequest.requested(OWNER).asked(listOf(question(1), question(2, "Which team?")))

        assertThat(request.status).isEqualTo(CvStatus.AWAITING_ANSWERS)
        assertThat(request.questions).hasSize(2)
    }

    @Test
    fun `goes straight to writing when the interviewer has no questions`() {
        assertThat(CvRequest.requested(OWNER).asked(emptyList()).status).isEqualTo(CvStatus.WRITING)
    }

    @Test
    fun `is asked at most five questions`() {
        val six = (1..6).map { question(it, "Question $it") }

        assertThatThrownBy { CvRequest.requested(OWNER).asked(six) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `is asked each question once`() {
        assertThatThrownBy { CvRequest.requested(OWNER).asked(listOf(question(1), question(1, "Again?"))) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `is only interviewed once`() {
        assertThatThrownBy { awaitingAnswers().asked(emptyList()) }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `moves on to writing once answered, a question left out counting as skipped`() {
        val request = awaitingAnswers(question(1), question(2, "Which team?")).answered(listOf(Answer(2, "Payments")))

        assertThat(request.status).isEqualTo(CvStatus.WRITING)
        assertThat(request.answers).containsExactly(Answer(1, null), Answer(2, "Payments"))
    }

    @Test
    fun `can skip every question`() {
        val request = awaitingAnswers().answered(emptyList())

        assertThat(request.status).isEqualTo(CvStatus.WRITING)
        assertThat(request.answers.single().skipped).isTrue()
    }

    @Test
    fun `refuses an answer to a question it was not asked`() {
        assertThatThrownBy { awaitingAnswers(question(1)).answered(listOf(Answer(9, "Hello"))) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `is only answered while waiting for answers`() {
        assertThatThrownBy { writing().answered(emptyList()) }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `needs a first round, then another until approved`() {
        val request = writing()
        assertThat(request.needsAnotherRound).isTrue()

        val rejectedOnce = request.withRound(Round(1, cv(), rejected()))
        assertThat(rejectedOnce.needsAnotherRound).isTrue()

        val approved = rejectedOnce.withRound(Round(2, cv(), review()))
        assertThat(approved.needsAnotherRound).isFalse()
    }

    @Test
    fun `stops after three rounds, approved or not`() {
        val request = (1..3).fold(writing()) { request, n -> request.withRound(Round(n, cv(), rejected())) }

        assertThat(request.needsAnotherRound).isFalse()
        assertThatThrownBy { request.withRound(Round(4, cv(), rejected())) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `takes its rounds in order`() {
        assertThatThrownBy { writing().withRound(Round(2, cv(), review())) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `only takes rounds while writing`() {
        assertThatThrownBy { awaitingAnswers().withRound(Round(1, cv(), review())) }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `completes with the approved draft`() {
        val approvedDraft = cv(summary = "The approved one.")
        val request =
            writing()
                .withRound(Round(1, cv(summary = "First."), rejected(score = 70)))
                .withRound(Round(2, approvedDraft, review(score = 85)))
                .completed()

        assertThat(request.status).isEqualTo(CvStatus.COMPLETED)
        assertThat(request.cv).isEqualTo(approvedDraft)
    }

    @Test
    fun `completes with the best scoring draft when none was approved`() {
        val best = cv(summary = "The best one.")
        val request =
            writing()
                .withRound(Round(1, cv(summary = "First."), rejected(score = 60)))
                .withRound(Round(2, best, rejected(score = 80)))
                .withRound(Round(3, cv(summary = "Third."), rejected(score = 75)))
                .completed()

        assertThat(request.cv).isEqualTo(best)
    }

    @Test
    fun `prefers the later draft on a tied score, as it had more feedback`() {
        val later = cv(summary = "Later.")
        val request =
            writing()
                .withRound(Round(1, cv(summary = "Earlier."), rejected(score = 70)))
                .withRound(Round(2, cv(summary = "Middle."), rejected(score = 50)))
                .withRound(Round(3, later, rejected(score = 70)))
                .completed()

        assertThat(request.cv).isEqualTo(later)
    }

    @Test
    fun `cannot complete while another round is needed`() {
        assertThatThrownBy { writing().completed() }.isInstanceOf(IllegalStateException::class.java)
        assertThatThrownBy { writing().withRound(Round(1, cv(), rejected())).completed() }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `fails with a reason, unless it has already finished`() {
        val failed = writing().failed("The writer gave no usable draft")

        assertThat(failed.status).isEqualTo(CvStatus.FAILED)
        assertThat(failed.failure).isEqualTo("The writer gave no usable draft")
        assertThatThrownBy { failed.failed("again") }.isInstanceOf(IllegalStateException::class.java)
    }
}
