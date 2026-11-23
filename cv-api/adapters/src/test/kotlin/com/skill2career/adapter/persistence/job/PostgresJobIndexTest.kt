package com.skill2career.adapter.persistence.job

import com.skill2career.adapter.persistence.PostgresTest
import com.skill2career.domain.job.JobFilter
import com.skill2career.domain.job.JobFixtures.FIRST_SEEN
import com.skill2career.domain.job.JobFixtures.job
import com.skill2career.domain.job.JobFixtures.posting
import com.skill2career.domain.job.JobPosting
import com.skill2career.domain.job.TrackedCompany
import com.skill2career.domain.job.Workplace
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import kotlin.test.Test

@Import(PostgresJobIndex::class, JobJpaAdapter::class, TrackedCompanyJpaAdapter::class)
class PostgresJobIndexTest : PostgresTest() {
    @Autowired
    lateinit var index: PostgresJobIndex

    @Autowired
    lateinit var jobs: JobJpaAdapter

    @Autowired
    lateinit var companies: TrackedCompanyJpaAdapter

    @Autowired
    lateinit var entities: EntityManager

    private val monzo: TrackedCompany by lazy { companies.all().single { it.name == "Monzo" } }
    private val gitlab: TrackedCompany by lazy { companies.all().single { it.name == "GitLab" } }

    private fun holds(vararg postings: Pair<TrackedCompany, JobPosting>) {
        jobs.saveAll(postings.map { (company, posting) -> job(company = company, posting = posting) })
        entities.flush()
    }

    private fun search(
        vararg terms: String,
        filter: JobFilter = JobFilter(),
        limit: Int = 50,
    ) = index.search(terms.toList(), filter, limit)

    @Test
    fun `finds the jobs that mention any of the terms, in the title or description, with their company`() {
        holds(
            monzo to posting("1", title = "Kotlin Engineer", description = "Payments."),
            gitlab to posting("2", title = "Backend Engineer", description = "We use Go and Postgres."),
            monzo to posting("3", title = "Designer", description = "Figma."),
        )

        val found = search("kotlin", "Postgres")

        assertThat(found.map { it.job.posting.externalId }).containsExactlyInAnyOrder("1", "2")
        assertThat(found.single { it.job.posting.externalId == "2" }.company).isEqualTo("GitLab")
    }

    @Test
    fun `finds a term of more than one word as a phrase`() {
        holds(
            monzo to posting("1", description = "Spring Boot services."),
            monzo to posting("2", description = "Spring cleaning, then boot camp."),
        )

        assertThat(search("Spring Boot").map { it.job.posting.externalId }).containsExactly("1")
    }

    @Test
    fun `takes a term with search syntax in it as plain words`() {
        holds(monzo to posting("1", description = "Kotlin."))

        assertThat(search("\"Kotlin", "-or- & | !")).hasSize(1)
    }

    @Test
    fun `never finds an inactive job`() {
        jobs.saveAll(listOf(job(company = monzo, posting = posting("1", description = "Kotlin."), inactiveSince = FIRST_SEEN)))
        entities.flush()

        assertThat(search("Kotlin")).isEmpty()
    }

    @Test
    fun `remote only finds just the jobs whose board says they are remote`() {
        holds(
            monzo to posting("1", description = "Kotlin.", workplace = Workplace.REMOTE),
            monzo to posting("2", description = "Kotlin.", workplace = Workplace.HYBRID),
            monzo to posting("3", description = "Kotlin.", workplace = null),
        )

        assertThat(search("Kotlin", filter = JobFilter(remoteOnly = true)).map { it.job.posting.externalId }).containsExactly("1")
    }

    @Test
    fun `a location finds the jobs whose location names it, whatever its case, taking its symbols as written`() {
        holds(
            monzo to posting("1", description = "Kotlin.", location = "Cardiff, London or Remote (UK)"),
            monzo to posting("2", description = "Kotlin.", location = "Dublin"),
            monzo to posting("3", description = "Kotlin.", location = null),
            monzo to posting("4", description = "Kotlin.", location = "100% remote"),
        )

        assertThat(search("Kotlin", filter = JobFilter(location = "london")).map { it.job.posting.externalId }).containsExactly("1")
        assertThat(search("Kotlin", filter = JobFilter(location = "%")).map { it.job.posting.externalId }).containsExactly("4")
    }

    @Test
    fun `the best text match comes first, and no more than the limit`() {
        holds(
            monzo to posting("1", title = "Engineer", description = "Some Kotlin."),
            monzo to posting("2", title = "Kotlin Engineer", description = "Kotlin, Kotlin and Kafka."),
            monzo to posting("3", title = "Engineer", description = "Kotlin and Kafka."),
        )

        assertThat(search("Kotlin", "Kafka", limit = 2).map { it.job.posting.externalId }).containsExactly("2", "3")
    }

    @Test
    fun `finds nothing for no terms`() {
        holds(monzo to posting("1"))

        assertThat(search()).isEmpty()
    }
}
