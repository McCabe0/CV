package com.skill2career.adapter.persistence.job

import com.skill2career.adapter.persistence.PostgresTest
import com.skill2career.domain.job.JobFixtures.job
import com.skill2career.domain.job.JobFixtures.posting
import com.skill2career.domain.job.TrackedCompany
import com.skill2career.domain.job.Workplace
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant
import kotlin.test.Test

@Import(JobJpaAdapter::class, TrackedCompanyJpaAdapter::class)
class JobJpaAdapterTest : PostgresTest() {
    @Autowired
    lateinit var adapter: JobJpaAdapter

    @Autowired
    lateinit var companies: TrackedCompanyJpaAdapter

    private val monzo: TrackedCompany by lazy { companies.all().single { it.name == "Monzo" } }
    private val gitlab: TrackedCompany by lazy { companies.all().single { it.name == "GitLab" } }

    @Test
    fun `counts each company's live jobs, leaving out closed ones and companies with none`() {
        val closed = Instant.parse("2026-11-23T06:00:00Z")
        adapter.saveAll(
            listOf(
                job(company = monzo, posting = posting("1")),
                job(company = monzo, posting = posting("2")),
                job(company = monzo, posting = posting("3"), inactiveSince = closed),
                job(company = gitlab, posting = posting("1"), inactiveSince = closed),
            ),
        )

        assertThat(adapter.liveCounts()).isEqualTo(mapOf(monzo.id!! to 2))
    }

    @Test
    fun `saves new jobs, giving each an id`() {
        adapter.saveAll(listOf(job(company = monzo, posting = posting("1")), job(company = monzo, posting = posting("2"))))

        val saved = adapter.forCompany(monzo.id!!)
        assertThat(saved.map { it.posting.externalId }).containsExactlyInAnyOrder("1", "2")
        assertThat(saved).allMatch { it.id != null }
    }

    @Test
    fun `keeps every part of a job, inactive ones too`() {
        val inactive =
            job(
                company = monzo,
                posting =
                    posting(
                        "1",
                        title = "Staff Engineer",
                        location = null,
                        description = "Line one.\nLine two.",
                        workplace = Workplace.HYBRID,
                    ),
                firstSeenAt = Instant.parse("2026-11-20T06:00:00Z"),
                inactiveSince = Instant.parse("2026-11-23T06:00:00Z"),
            )

        adapter.saveAll(listOf(inactive))

        assertThat(adapter.forCompany(monzo.id!!).single().copy(id = null)).isEqualTo(inactive)
    }

    @Test
    fun `updates a job it has, by its id`() {
        adapter.saveAll(listOf(job(company = monzo, posting = posting("1", title = "Engineer"))))
        val stored = adapter.forCompany(monzo.id!!).single()

        adapter.saveAll(listOf(stored.copy(posting = posting("1", title = "Senior Engineer"))))

        assertThat(adapter.forCompany(monzo.id!!).single()).isEqualTo(stored.copy(posting = posting("1", title = "Senior Engineer")))
    }

    @Test
    fun `gives only the company's own jobs`() {
        adapter.saveAll(listOf(job(company = monzo, posting = posting("1")), job(company = gitlab, posting = posting("9"))))

        assertThat(adapter.forCompany(gitlab.id!!).map { it.posting.externalId }).containsExactly("9")
    }

    @Test
    fun `holds a board's posting once per company, so a crawl can never copy it`() {
        adapter.saveAll(listOf(job(company = monzo, posting = posting("1"))))

        assertThatThrownBy {
            adapter.saveAll(listOf(job(company = monzo, posting = posting("1"))))
            adapter.forCompany(monzo.id!!)
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `keeps a long description`() {
        val long = "x".repeat(60_000)

        adapter.saveAll(listOf(job(company = monzo, posting = posting("1", description = long))))

        assertThat(
            adapter
                .forCompany(monzo.id!!)
                .single()
                .posting.description,
        ).hasSize(60_000)
    }

    @Test
    fun `finds a job by its id, and nothing for an id it never had`() {
        adapter.saveAll(listOf(job(company = monzo, posting = posting("1", title = "Staff Engineer"))))
        val saved = adapter.forCompany(monzo.id!!).single()

        assertThat(adapter.find(saved.id!!)).isEqualTo(saved)
        assertThat(adapter.find(-1)).isNull()
    }
}
