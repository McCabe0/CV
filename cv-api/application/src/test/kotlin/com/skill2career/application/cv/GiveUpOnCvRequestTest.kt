package com.skill2career.application.cv

import com.skill2career.domain.cv.CvFixtures.cv
import com.skill2career.domain.cv.CvFixtures.review
import com.skill2career.domain.cv.CvFixtures.writing
import com.skill2career.domain.cv.CvRequest
import com.skill2career.domain.cv.CvStatus
import com.skill2career.domain.cv.Round
import com.skill2career.domain.profile.ProfileFixtures.OWNER
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

class GiveUpOnCvRequestTest {
    private val requests = InMemoryCvRequestStore()
    private val giveUp = GiveUpOnCvRequest(requests)

    @Test
    fun `fails a request whose work could not be done, saying plainly to try again later`() {
        val id = requests.save(CvRequest.requested(OWNER)).id!!

        val request = giveUp.giveUp(id)!!

        assertThat(request.status).isEqualTo(CvStatus.FAILED)
        assertThat(request.failure).isEqualTo("Our writing service is busy right now. Please try again in a few minutes.")
        assertThat(requests.find(id)).isEqualTo(request)
    }

    @Test
    fun `keeps the rounds already written, as a failed request shows how far it got`() {
        val id = requests.save(writing().copy(id = null).withRound(Round(1, cv(), review(approved = false)))).id!!

        assertThat(giveUp.giveUp(id)!!.rounds).hasSize(1)
    }

    @Test
    fun `leaves a finished request as it is`() {
        val finished = requests.save(writing().copy(id = null).withRound(Round(1, cv(), review())).completed())

        assertThat(giveUp.giveUp(finished.id!!)).isEqualTo(finished)
        assertThat(requests.find(finished.id!!)!!.status).isEqualTo(CvStatus.COMPLETED)
    }

    @Test
    fun `does nothing for a request that does not exist`() {
        assertThat(giveUp.giveUp(999)).isNull()
        assertThat(requests.saves).isEmpty()
    }
}
