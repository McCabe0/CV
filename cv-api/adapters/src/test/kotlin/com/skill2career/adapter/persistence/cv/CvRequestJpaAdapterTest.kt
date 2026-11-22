package com.skill2career.adapter.persistence.cv

import com.skill2career.adapter.persistence.PostgresTest
import com.skill2career.domain.cv.Answer
import com.skill2career.domain.cv.CvFixtures.awaitingAnswers
import com.skill2career.domain.cv.CvFixtures.cv
import com.skill2career.domain.cv.CvFixtures.question
import com.skill2career.domain.cv.CvFixtures.rejected
import com.skill2career.domain.cv.CvFixtures.review
import com.skill2career.domain.cv.CvRequest
import com.skill2career.domain.cv.CvStatus
import com.skill2career.domain.cv.Issue
import com.skill2career.domain.cv.IssueKind
import com.skill2career.domain.cv.Round
import com.skill2career.domain.profile.ProfileFixtures.OWNER
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import kotlin.test.Test

@Import(CvRequestJpaAdapter::class)
class CvRequestJpaAdapterTest : PostgresTest() {
    @Autowired
    lateinit var adapter: CvRequestJpaAdapter

    @Test
    fun `gives a new request its id`() {
        val saved = adapter.save(CvRequest.requested(OWNER))

        assertThat(saved.id).isNotNull()
        assertThat(adapter.find(saved.id!!)).isEqualTo(saved)
    }

    @Test
    fun `keeps every part of a finished request`() {
        val finished =
            awaitingAnswers(question(1, "By how much?"), question(2, "Which team?"))
                .copy(id = null)
                .answered(listOf(Answer(1, "By 40%")))
                .withRound(Round(1, cv(summary = "First."), rejected(55, Issue(IssueKind.INVENTED_FACT, "No such employer"))))
                .withRound(Round(2, cv(summary = "Second."), review(91)))
                .completed()

        val saved = adapter.save(finished)

        assertThat(adapter.find(saved.id!!)).isEqualTo(saved)
        assertThat(
            adapter
                .find(saved.id!!)!!
                .rounds[0]
                .review.approved,
        ).isFalse()
    }

    @Test
    fun `keeps why a request failed`() {
        val saved = adapter.save(CvRequest.requested(OWNER).failed("The interviewer failed: not json"))

        assertThat(adapter.find(saved.id!!)!!.failure).isEqualTo("The interviewer failed: not json")
    }

    @Test
    fun `saves each step over the last`() {
        val requested = adapter.save(CvRequest.requested(OWNER))

        adapter.save(requested.asked(listOf(question(1))))

        assertThat(adapter.find(requested.id!!)!!.status).isEqualTo(CvStatus.AWAITING_ANSWERS)
    }

    @Test
    fun `finds nothing for an id it does not have`() {
        assertThat(adapter.find(999_999)).isNull()
    }
}
