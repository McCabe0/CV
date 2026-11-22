package com.skill2career.application.cv

import com.skill2career.domain.cv.Answer
import com.skill2career.domain.cv.CvFixtures.awaitingAnswers
import com.skill2career.domain.cv.CvFixtures.question
import com.skill2career.domain.cv.CvStatus
import com.skill2career.domain.profile.OwnerId
import com.skill2career.domain.profile.ProfileFixtures.OWNER
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import kotlin.test.Test

class AnswerQuestionsTest {
    private val requests = InMemoryCvRequestStore()
    private val waiting = requests.save(awaitingAnswers(question(1), question(2, "Which team?")).copy(id = null))
    private val answerQuestions = AnswerQuestions(requests)

    @Test
    fun `keeps the candidate's answers and moves on to writing`() {
        val request = answerQuestions.answer(OWNER, waiting.id!!, listOf(Answer(1, "By 40%")))

        assertThat(request.status).isEqualTo(CvStatus.WRITING)
        assertThat(requests.find(waiting.id!!)!!.answers).containsExactly(Answer(1, "By 40%"), Answer(2, null))
    }

    @Test
    fun `only lets the owner answer`() {
        assertThatThrownBy { answerQuestions.answer(OwnerId("someone-else"), waiting.id!!, emptyList()) }
            .isInstanceOf(CvRequestNotFound::class.java)
        assertThat(requests.find(waiting.id!!)!!.status).isEqualTo(CvStatus.AWAITING_ANSWERS)
    }
}
