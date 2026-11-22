package com.skill2career.application.cv

import com.skill2career.application.profile.InMemoryProfileStore
import com.skill2career.application.profile.ProfileNotFound
import com.skill2career.domain.cv.CvFixtures.awaitingAnswers
import com.skill2career.domain.cv.CvStatus
import com.skill2career.domain.profile.OwnerId
import com.skill2career.domain.profile.ProfileFixtures.OWNER
import com.skill2career.domain.profile.ProfileFixtures.profile
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import kotlin.test.Test

class RequestCvTest {
    private val profiles = InMemoryProfileStore()
    private val requests = InMemoryCvRequestStore()

    @Test
    fun `starts a request for a candidate with a profile`() {
        profiles.save(profile())

        val request = RequestCv(profiles, requests).request(OWNER)

        assertThat(request.id).isNotNull()
        assertThat(request.status).isEqualTo(CvStatus.REQUESTED)
        assertThat(requests.find(request.id!!)).isEqualTo(request)
    }

    @Test
    fun `needs a profile, as the CV is written only from it`() {
        assertThatThrownBy { RequestCv(profiles, requests).request(OWNER) }.isInstanceOf(ProfileNotFound::class.java)
    }

    @Test
    fun `finds the owner's own request`() {
        val saved = requests.save(awaitingAnswers().copy(id = null))

        assertThat(FindCvRequest(requests).find(OWNER, saved.id!!)).isEqualTo(saved)
    }

    @Test
    fun `never shows one candidate another's request, as if it did not exist`() {
        val saved = requests.save(awaitingAnswers().copy(id = null))

        assertThatThrownBy { FindCvRequest(requests).find(OwnerId("someone-else"), saved.id!!) }
            .isInstanceOf(CvRequestNotFound::class.java)
        assertThatThrownBy { FindCvRequest(requests).find(OWNER, 999) }.isInstanceOf(CvRequestNotFound::class.java)
    }
}
