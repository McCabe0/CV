package com.skill2career.application.cv

import com.skill2career.application.profile.InMemoryProfileStore
import com.skill2career.domain.cv.CvFixtures.awaitingAnswers
import com.skill2career.domain.cv.CvFixtures.cv
import com.skill2career.domain.cv.CvFixtures.question
import com.skill2career.domain.cv.CvFixtures.review
import com.skill2career.domain.cv.CvFixtures.writing
import com.skill2career.domain.cv.CvRequest
import com.skill2career.domain.cv.CvStatus
import com.skill2career.domain.profile.ProfileFixtures.OWNER
import com.skill2career.domain.profile.ProfileFixtures.profile
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

class AdvanceCvRequestTest {
    private val profiles = InMemoryProfileStore().apply { save(profile()) }
    private val requests = InMemoryCvRequestStore()
    private var drafts = 0

    private fun advance(
        id: Long,
        questions: List<com.skill2career.domain.cv.Question> = emptyList(),
    ) = AdvanceCvRequest(
        InterviewCandidate(requests, profiles) { questions },
        WriteCv(
            requests,
            profiles,
            {
                drafts++
                cv()
            },
            { _, _ -> review() },
        ),
    ).advance(id)

    @Test
    fun `a new request with nothing to ask goes straight on to the writing`() {
        val id = requests.save(CvRequest.requested(OWNER)).id!!

        val request = advance(id)

        assertThat(request.status).isEqualTo(CvStatus.COMPLETED)
        assertThat(drafts).isEqualTo(1)
    }

    @Test
    fun `a new request with questions waits for the candidate, unwritten`() {
        val id = requests.save(CvRequest.requested(OWNER)).id!!

        val request = advance(id, listOf(question()))

        assertThat(request.status).isEqualTo(CvStatus.AWAITING_ANSWERS)
        assertThat(drafts).isZero()
    }

    @Test
    fun `an answered request is written`() {
        val id = requests.save(writing().copy(id = null)).id!!

        assertThat(advance(id).status).isEqualTo(CvStatus.COMPLETED)
    }

    @Test
    fun `a request already finished, or still waiting, is left as it is`() {
        val waiting = requests.save(awaitingAnswers().copy(id = null))

        assertThat(advance(waiting.id!!)).isEqualTo(waiting)
        assertThat(drafts).isZero()
    }
}
