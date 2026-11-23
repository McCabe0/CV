package com.skill2career.application.cv

import com.skill2career.application.job.InMemoryJobStore
import com.skill2career.application.job.JobNotFound
import com.skill2career.application.profile.InMemoryProfileStore
import com.skill2career.application.profile.ProfileNotFound
import com.skill2career.domain.cv.CvFixtures.awaitingAnswers
import com.skill2career.domain.cv.CvStatus
import com.skill2career.domain.job.JobFixtures.COMPANY
import com.skill2career.domain.job.JobFixtures.FIRST_SEEN
import com.skill2career.domain.job.JobFixtures.job
import com.skill2career.domain.job.JobFixtures.posting
import com.skill2career.domain.profile.OwnerId
import com.skill2career.domain.profile.ProfileFixtures.OWNER
import com.skill2career.domain.profile.ProfileFixtures.profile
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import kotlin.test.Test

class RequestCvTest {
    private val profiles = InMemoryProfileStore()
    private val requests = InMemoryCvRequestStore()
    private val jobs = InMemoryJobStore()
    private val requestCv = RequestCv(profiles, requests, jobs)

    @Test
    fun `starts a request for a candidate with a profile`() {
        profiles.save(profile())

        val request = requestCv.request(OWNER)

        assertThat(request.id).isNotNull()
        assertThat(request.status).isEqualTo(CvStatus.REQUESTED)
        assertThat(request.jobId).isNull()
        assertThat(requests.find(request.id!!)).isEqualTo(request)
    }

    @Test
    fun `queues the new request for the cv-worker, in the same save`() {
        profiles.save(profile())

        val request = requestCv.request(OWNER)

        assertThat(requests.queued).containsExactly(request.id)
    }

    @Test
    fun `needs a profile, as the CV is written only from it`() {
        assertThatThrownBy { requestCv.request(OWNER) }.isInstanceOf(ProfileNotFound::class.java)
    }

    @Test
    fun `starts a CV for one listed job, to be aimed at it`() {
        profiles.save(profile())
        jobs.saveAll(listOf(job(posting = posting("1"))))
        val jobId = jobs.forCompany(COMPANY.id!!).single().id!!

        val request = requestCv.request(OWNER, jobId)

        assertThat(request.jobId).isEqualTo(jobId)
        assertThat(requests.queued).containsExactly(request.id)
    }

    @Test
    fun `a CV for a job we do not have, or no longer listed, is not started`() {
        profiles.save(profile())
        jobs.saveAll(listOf(job(posting = posting("1"), inactiveSince = FIRST_SEEN)))
        val gone = jobs.forCompany(COMPANY.id!!).single().id!!

        assertThatThrownBy { requestCv.request(OWNER, 999) }.isInstanceOf(JobNotFound::class.java)
        assertThatThrownBy { requestCv.request(OWNER, gone) }.isInstanceOf(JobNotFound::class.java)
        assertThat(requests.queued).isEmpty()
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
