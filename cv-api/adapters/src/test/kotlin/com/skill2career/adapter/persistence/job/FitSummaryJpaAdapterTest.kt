package com.skill2career.adapter.persistence.job

import com.skill2career.adapter.messaging.FitMessage
import com.skill2career.adapter.messaging.OutboxRepo
import com.skill2career.adapter.messaging.Topics
import com.skill2career.adapter.persistence.PostgresTest
import com.skill2career.domain.job.FitStatus
import com.skill2career.domain.job.FitSummary
import com.skill2career.domain.job.JobFixtures.job
import com.skill2career.domain.job.JobFixtures.posting
import com.skill2career.domain.profile.OwnerId
import com.skill2career.domain.profile.ProfileFixtures.OWNER
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import kotlin.test.Test

@Import(FitSummaryJpaAdapter::class, JobJpaAdapter::class, TrackedCompanyJpaAdapter::class)
class FitSummaryJpaAdapterTest : PostgresTest() {
    @Autowired
    lateinit var adapter: FitSummaryJpaAdapter

    @Autowired
    lateinit var jobs: JobJpaAdapter

    @Autowired
    lateinit var companies: TrackedCompanyJpaAdapter

    @Autowired
    lateinit var outbox: OutboxRepo

    @Autowired
    lateinit var entities: EntityManager

    private val jobId: Long by lazy {
        val monzo = companies.all().single { it.name == "Monzo" }
        jobs.saveAll(listOf(job(company = monzo, posting = posting("fit-1"))))
        jobs.forCompany(monzo.id!!).single().id!!
    }

    @Test
    fun `gives a new summary its id, and finds it by id, and by candidate and job`() {
        val saved = adapter.save(FitSummary(ownerId = OWNER, jobId = jobId))

        assertThat(saved.id).isNotNull()
        assertThat(adapter.find(saved.id!!)).isEqualTo(saved)
        assertThat(adapter.find(OWNER, jobId)).isEqualTo(saved)
    }

    @Test
    fun `keeps every part of a summary, written or failed`() {
        val written = adapter.save(FitSummary(ownerId = OWNER, jobId = jobId).written("Your Kotlin fits."))
        assertThat(adapter.find(written.id!!)).isEqualTo(written)

        val failed = adapter.save(written.failed("busy"))
        assertThat(adapter.find(written.id!!)).isEqualTo(failed)
        assertThat(failed.status).isEqualTo(FitStatus.FAILED)
    }

    @Test
    fun `queues the summary for the cv-worker in the outbox, in the same save, keyed by the summary`() {
        val saved = adapter.save(FitSummary(ownerId = OWNER, jobId = jobId), queueWork = true)

        val queued = outbox.findAll().single { it.topic == Topics.JOBS_FIT }
        assertThat(queued.messageKey).isEqualTo(saved.id.toString())
        assertThat(FitMessage.read(queued.payload)).isEqualTo(FitMessage(queued.eventId, saved.id!!))
    }

    @Test
    fun `queues nothing for a plain save`() {
        adapter.save(FitSummary(ownerId = OWNER, jobId = jobId))

        assertThat(outbox.findAll().map { it.topic }).doesNotContain(Topics.JOBS_FIT)
    }

    @Test
    fun `holds one summary per candidate and job, so two asks at once cannot both write one`() {
        adapter.save(FitSummary(ownerId = OWNER, jobId = jobId))

        assertThatThrownBy {
            adapter.save(FitSummary(ownerId = OWNER, jobId = jobId))
            entities.flush()
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `finds nothing for another candidate, or an id it does not have`() {
        adapter.save(FitSummary(ownerId = OWNER, jobId = jobId))

        assertThat(adapter.find(OwnerId("other"), jobId)).isNull()
        assertThat(adapter.find(-1)).isNull()
    }
}
