package com.skill2career.application.job

import com.skill2career.application.cv.AgentFailure
import com.skill2career.application.profile.InMemoryProfileStore
import com.skill2career.application.profile.ProfileNotFound
import com.skill2career.domain.job.FitStatus
import com.skill2career.domain.job.FitSummary
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

class FitSummaryUseCasesTest {
    private val profiles = InMemoryProfileStore().apply { save(profile()) }
    private val companies = InMemoryTrackedCompanyStore(COMPANY)
    private val jobs = InMemoryJobStore()
    private val summaries = InMemoryFitSummaryStore()
    private val explainer = FakeFitExplainer()

    private val request = RequestFitSummary(profiles, jobs, summaries)
    private val find = FindFitSummary(summaries)
    private val write = WriteFitSummary(summaries, profiles, jobs, companies, explainer)
    private val giveUp = GiveUpOnFitSummary(summaries)

    private val jobId: Long by lazy {
        jobs.saveAll(listOf(job(posting = posting("1", title = "Backend Engineer"))))
        jobs.forCompany(COMPANY.id!!).single().id!!
    }

    // Asking.

    @Test
    fun `asking for a job's summary queues it to be written`() {
        val asked = request.request(OWNER, jobId)

        assertThat(asked.status).isEqualTo(FitStatus.WRITING)
        assertThat(summaries.queued).containsExactly(asked.id)
    }

    @Test
    fun `asking again gives the summary already asked for, never writing a second`() {
        val first = request.request(OWNER, jobId)

        val second = request.request(OWNER, jobId)

        assertThat(second).isEqualTo(first)
        assertThat(summaries.queued).containsExactly(first.id)
    }

    @Test
    fun `asking again after it failed writes it afresh`() {
        val failed = summaries.save(FitSummary(ownerId = OWNER, jobId = jobId).failed("busy"))

        val again = request.request(OWNER, jobId)

        assertThat(again.id).isEqualTo(failed.id)
        assertThat(again.status).isEqualTo(FitStatus.WRITING)
        assertThat(summaries.queued).containsExactly(failed.id)
    }

    @Test
    fun `each candidate has their own summary of a job`() {
        profiles.save(profile(ownerId = OwnerId("other")))
        val mine = request.request(OWNER, jobId)

        val theirs = request.request(OwnerId("other"), jobId)

        assertThat(theirs.id).isNotEqualTo(mine.id)
    }

    @Test
    fun `a job we do not have, or that is no longer listed, has no summary`() {
        jobs.saveAll(listOf(job(posting = posting("2"), inactiveSince = FIRST_SEEN)))
        val gone = jobs.forCompany(COMPANY.id!!).single { !it.active }.id!!

        assertThatThrownBy { request.request(OWNER, 999) }.isInstanceOf(JobNotFound::class.java)
        assertThatThrownBy { request.request(OWNER, gone) }.isInstanceOf(JobNotFound::class.java)
        assertThat(summaries.queued).isEmpty()
    }

    @Test
    fun `a candidate with no profile cannot ask`() {
        assertThatThrownBy { request.request(OwnerId("stranger"), jobId) }.isInstanceOf(ProfileNotFound::class.java)
    }

    // Finding.

    @Test
    fun `a candidate finds their summary of a job`() {
        val asked = request.request(OWNER, jobId)

        assertThat(find.find(OWNER, jobId)).isEqualTo(asked)
    }

    @Test
    fun `another candidate's summary is not found`() {
        request.request(OWNER, jobId)

        assertThatThrownBy { find.find(OwnerId("other"), jobId) }.isInstanceOf(FitSummaryNotFound::class.java)
    }

    // Writing, on the cv-worker.

    @Test
    fun `writes why the job fits, from the candidate's profile and the job`() {
        val asked = request.request(OWNER, jobId)
        explainer.says("Your Kotlin is what Monzo asks for.")

        val written = write.write(asked.id!!)

        assertThat(written.status).isEqualTo(FitStatus.READY)
        assertThat(written.text).isEqualTo("Your Kotlin is what Monzo asks for.")
        assertThat(
            explainer.asked
                .single()
                .first.ownerId,
        ).isEqualTo(OWNER)
        assertThat(
            explainer.asked
                .single()
                .second.company,
        ).isEqualTo("Monzo")
        assertThat(
            explainer.asked
                .single()
                .second.job.posting.title,
        ).isEqualTo("Backend Engineer")
        assertThat(find.find(OWNER, jobId)).isEqualTo(written)
    }

    @Test
    fun `writing it twice, as a message can come twice, asks the LLM once`() {
        val asked = request.request(OWNER, jobId)
        explainer.says("It fits.")

        write.write(asked.id!!)
        write.write(asked.id!!)

        assertThat(explainer.asked).hasSize(1)
    }

    @Test
    fun `fails, saying so, when the LLM gives no usable answer`() {
        val asked = request.request(OWNER, jobId)
        explainer.fails()

        val failed = write.write(asked.id!!)

        assertThat(failed.status).isEqualTo(FitStatus.FAILED)
        assertThat(failed.failure).isNotBlank()
    }

    @Test
    fun `lets any other error through, so the message is retried`() {
        val asked = request.request(OWNER, jobId)
        explainer.breaks()

        assertThatThrownBy { write.write(asked.id!!) }.isInstanceOf(IllegalStateException::class.java)
        assertThat(find.find(OWNER, jobId).status).isEqualTo(FitStatus.WRITING)
    }

    @Test
    fun `fails when the job stopped being listed before it was written`() {
        val asked = request.request(OWNER, jobId)
        jobs.saveAll(listOf(jobs.forCompany(COMPANY.id!!).single().copy(inactiveSince = FIRST_SEEN)))

        val failed = write.write(asked.id!!)

        assertThat(failed.status).isEqualTo(FitStatus.FAILED)
        assertThat(explainer.asked).isEmpty()
    }

    @Test
    fun `a summary that no longer exists cannot be written`() {
        assertThatThrownBy { write.write(999) }.isInstanceOf(FitSummaryNotFound::class.java)
    }

    // Giving up, after every retry.

    @Test
    fun `gives up on a summary still being written, so the candidate can ask again later`() {
        val asked = request.request(OWNER, jobId)

        val failed = giveUp.giveUp(asked.id!!)!!

        assertThat(failed.status).isEqualTo(FitStatus.FAILED)
        assertThat(failed.failure).contains("again")
    }

    @Test
    fun `never gives up on a summary already written`() {
        val asked = request.request(OWNER, jobId)
        explainer.says("It fits.")
        write.write(asked.id!!)

        assertThat(giveUp.giveUp(asked.id!!)!!.status).isEqualTo(FitStatus.READY)
    }
}
