package com.skill2career.adapter.persistence.job

import com.skill2career.adapter.persistence.PostgresTest
import com.skill2career.domain.job.CrawlOutcome
import com.skill2career.domain.job.CrawlResult
import com.skill2career.domain.job.TrackedCompany
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import java.time.Instant
import kotlin.test.Test

@Import(CrawlResultJpaAdapter::class, TrackedCompanyJpaAdapter::class)
class CrawlResultJpaAdapterTest : PostgresTest() {
    @Autowired
    lateinit var adapter: CrawlResultJpaAdapter

    @Autowired
    lateinit var companies: TrackedCompanyJpaAdapter

    private val monzo: TrackedCompany by lazy { companies.all().single { it.name == "Monzo" } }
    private val gitlab: TrackedCompany by lazy { companies.all().single { it.name == "GitLab" } }
    private val morning = Instant.parse("2026-11-23T06:00:00Z")

    @Test
    fun `has no crawls before the first`() {
        assertThat(adapter.latest()).isEmpty()
    }

    @Test
    fun `keeps every part of each company's crawl`() {
        val worked = CrawlResult(monzo.id!!, morning, CrawlOutcome.WORKED, found = 53, added = 4, closed = 2)
        val parked = CrawlResult.parked(gitlab.id!!, morning, "503 from the board")

        adapter.record(worked)
        adapter.record(parked)

        assertThat(adapter.latest()).isEqualTo(mapOf(monzo.id!! to worked, gitlab.id!! to parked))
    }

    @Test
    fun `a company's newer crawl replaces its last`() {
        adapter.record(CrawlResult.failed(monzo.id!!, morning, "503 from the board"))
        val retried = CrawlResult(monzo.id!!, morning.plusSeconds(60), CrawlOutcome.WORKED, found = 53)

        adapter.record(retried)

        assertThat(adapter.latest()).isEqualTo(mapOf(monzo.id!! to retried))
    }

    @Test
    fun `cuts a very long error, so one board's stack trace cannot fill the table`() {
        adapter.record(CrawlResult.failed(monzo.id!!, morning, "x".repeat(5_000)))

        assertThat(adapter.latest()[monzo.id]!!.error).hasSize(1_000)
    }
}
