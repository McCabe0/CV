package com.skill2career.application.job

import com.skill2career.domain.job.JobFixtures.COMPANY
import com.skill2career.domain.job.JobFixtures.posting
import com.skill2career.domain.job.SourceType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test

class CrawlCompanyTest {
    private val now = Instant.parse("2026-11-23T06:00:00Z")
    private val companies = InMemoryTrackedCompanyStore(COMPANY)
    private val jobs = InMemoryJobStore()
    private val greenhouse = FakeJobSource(SourceType.GREENHOUSE)
    private val lever = FakeJobSource(SourceType.LEVER)
    private val crawl = CrawlCompany(companies, listOf(greenhouse, lever), jobs, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `saves what the company's own board lists`() {
        greenhouse.lists(COMPANY, posting("1"), posting("2"))

        crawl.crawl(COMPANY.id!!)

        assertThat(jobs.forCompany(COMPANY.id!!).map { it.posting.externalId }).containsExactly("1", "2")
        assertThat(jobs.forCompany(COMPANY.id!!)).allMatch { it.active && it.firstSeenAt == now }
        assertThat(lever.fetched).isEmpty()
    }

    @Test
    fun `marks a job its board no longer lists inactive`() {
        greenhouse.lists(COMPANY, posting("1"), posting("2"))
        crawl.crawl(COMPANY.id!!)
        greenhouse.lists(COMPANY, posting("2"))

        crawl.crawl(COMPANY.id!!)

        assertThat(jobs.forCompany(COMPANY.id!!).filter { it.active }.map { it.posting.externalId }).containsExactly("2")
    }

    @Test
    fun `saves only what changed`() {
        greenhouse.lists(COMPANY, posting("1"), posting("2"))
        crawl.crawl(COMPANY.id!!)
        greenhouse.lists(COMPANY, posting("1"), posting("2", title = "Staff Engineer"))

        crawl.crawl(COMPANY.id!!)

        assertThat(jobs.lastSaved.map { it.posting.title }).containsExactly("Staff Engineer")
    }

    @Test
    fun `leaves the jobs as they were when the board fails, so the crawl can be retried`() {
        greenhouse.lists(COMPANY, posting("1"))
        crawl.crawl(COMPANY.id!!)
        greenhouse.fails()

        assertThatThrownBy { crawl.crawl(COMPANY.id!!) }.isInstanceOf(IllegalStateException::class.java)

        assertThat(jobs.forCompany(COMPANY.id!!)).allMatch { it.active }
    }

    @Test
    fun `a company no longer tracked is not found`() {
        assertThatThrownBy { crawl.crawl(99) }.isInstanceOf(TrackedCompanyNotFound::class.java)
    }

    @Test
    fun `a company whose board has no source cannot be crawled`() {
        val ashby = companies.save(COMPANY.copy(id = null, source = SourceType.ASHBY, boardToken = "ramp"))

        assertThatThrownBy { crawl.crawl(ashby.id!!) }.isInstanceOf(NoJobSource::class.java)
    }
}
