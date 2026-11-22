package com.skill2career.application.cv

import com.skill2career.application.profile.InMemoryProfileStore
import com.skill2career.domain.cv.CvFixtures.question
import com.skill2career.domain.cv.CvRequest
import com.skill2career.domain.cv.CvStatus
import com.skill2career.domain.profile.ProfileFixtures.OWNER
import com.skill2career.domain.profile.ProfileFixtures.profile
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

class InterviewCandidateTest {
    private val profiles = InMemoryProfileStore().apply { save(profile()) }
    private val requests = InMemoryCvRequestStore()
    private val requested = requests.save(CvRequest.requested(OWNER))

    private fun interview(interviewer: Interviewer) = InterviewCandidate(requests, profiles, interviewer).interview(requested.id!!)

    @Test
    fun `asks the interviewer's questions about the candidate's profile`() {
        var seen: Any? = null
        val request =
            interview { profile ->
                seen = profile
                listOf(question(1))
            }

        assertThat(seen).isEqualTo(profiles.findByOwner(OWNER))
        assertThat(request.status).isEqualTo(CvStatus.AWAITING_ANSWERS)
        assertThat(requests.find(requested.id!!)!!.questions).containsExactly(question(1))
    }

    @Test
    fun `keeps only the first five, should the interviewer ask more`() {
        val request = interview { (1..7).map { question(it, "Question $it") } }

        assertThat(request.questions.map { it.id }).containsExactly(1, 2, 3, 4, 5)
    }

    @Test
    fun `fails the request when the interviewer gives no usable answer`() {
        val request = interview { throw AgentFailure("not json") }

        assertThat(request.status).isEqualTo(CvStatus.FAILED)
        assertThat(request.failure).isEqualTo("The interviewer failed: not json")
    }

    @Test
    fun `does nothing for a request already interviewed, so a repeated message is harmless`() {
        interview { listOf(question(1)) }
        var askedAgain = false

        val request =
            interview {
                askedAgain = true
                emptyList()
            }

        assertThat(askedAgain).isFalse()
        assertThat(request.status).isEqualTo(CvStatus.AWAITING_ANSWERS)
    }

    @Test
    fun `fails the request when the profile has gone`() {
        val request = InterviewCandidate(requests, InMemoryProfileStore(), { emptyList() }).interview(requested.id!!)

        assertThat(request.status).isEqualTo(CvStatus.FAILED)
    }

    @Test
    fun `refuses a request that does not exist`() {
        val thrown = runCatching { InterviewCandidate(requests, profiles, { emptyList() }).interview(999) }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(CvRequestNotFound::class.java)
    }
}
