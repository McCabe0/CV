package com.skill2career.application.job

import com.skill2career.domain.job.CrawlOutcome
import com.skill2career.domain.job.CrawlResult
import com.skill2career.domain.job.JobFixtures.COMPANY
import com.skill2career.domain.job.JobFixtures.job
import com.skill2career.domain.job.JobFixtures.posting
import com.skill2career.domain.job.SourceType
import com.skill2career.domain.job.TrackedCompany
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test

class CompanyAdminTest {
    private val now = Instant.parse("2026-11-23T06:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val companies = InMemoryTrackedCompanyStore(COMPANY)
    private val jobs = InMemoryJobStore()
    private val results = InMemoryCrawlResults()
    private val queue = RecordingCrawlQueue()
    private val greenhouse = FakeJobSource(SourceType.GREENHOUSE)
    private val lever = FakeJobSource(SourceType.LEVER)

    private val list = ListCompanies(companies, jobs, results)
    private val track = TrackCompany(companies, listOf(greenhouse, lever), queue)
    private val stop = StopTracking(companies, jobs, clock)
    private val crawlNow = CrawlNow(companies, queue)
    private val recordFailure = RecordCrawlFailure(results, clock)

    @Test
    fun `lists every company with its live jobs and its last crawl, tracked ones first, by name`() {
        val spotify = companies.save(TrackedCompany(name = "Spotify", source = SourceType.LEVER, boardToken = "spotify"))
        val ramp = companies.save(TrackedCompany(name = "Ramp", source = SourceType.ASHBY, boardToken = "ramp", untrackedAt = now))
        jobs.saveAll(listOf(job(posting = posting("1")), job(posting = posting("2")), job(posting = posting("3"), inactiveSince = now)))
        val lastCrawl = CrawlResult(COMPANY.id!!, now, CrawlOutcome.WORKED, found = 2)
        results.record(CrawlResult.failed(COMPANY.id!!, now.minusSeconds(3600), "the board is down"))
        results.record(lastCrawl)

        val listed = list.list()

        assertThat(listed.map { it.company.name }).containsExactly("Monzo", "Spotify", "Ramp")
        assertThat(listed[0]).isEqualTo(CompanyHealth(COMPANY, liveJobs = 2, lastCrawl = lastCrawl))
        assertThat(listed[1]).isEqualTo(CompanyHealth(spotify, liveJobs = 0, lastCrawl = null))
        assertThat(listed[2].company).isEqualTo(ramp)
    }

    @Test
    fun `tracks a company once its board answers, and queues its crawl at once`() {
        val added = track.track("Spotify", SourceType.LEVER, "spotify")

        assertThat(lever.fetched.single().boardToken).isEqualTo("spotify")
        assertThat(companies.find(added.id!!)).isEqualTo(TrackedCompany(added.id, "Spotify", SourceType.LEVER, "spotify"))
        assertThat(queue.queued).containsExactly(added.id)
    }

    @Test
    fun `tracks nothing when the board cannot be read, eg its token is wrong`() {
        lever.fails()

        assertThatThrownBy { track.track("Spotify", SourceType.LEVER, "spotfy") }.isInstanceOf(IllegalStateException::class.java)

        assertThat(companies.all()).containsExactly(COMPANY)
        assertThat(queue.queued).isEmpty()
    }

    @Test
    fun `refuses a board that is already tracked`() {
        assertThatThrownBy { track.track("Monzo again", SourceType.GREENHOUSE, "monzo") }
            .isInstanceOf(CompanyAlreadyTracked::class.java)

        assertThat(greenhouse.fetched).isEmpty()
    }

    @Test
    fun `tracks an untracked company again, under its new name, keeping its jobs and history`() {
        companies.save(COMPANY.untracked(now))

        val again = track.track("Monzo Bank", SourceType.GREENHOUSE, "monzo")

        assertThat(again).isEqualTo(COMPANY.copy(name = "Monzo Bank"))
        assertThat(companies.all()).containsExactly(again)
        assertThat(queue.queued).containsExactly(COMPANY.id)
    }

    @Test
    fun `cannot track a board no source reads`() {
        assertThatThrownBy { track.track("Ramp", SourceType.ASHBY, "ramp") }.isInstanceOf(NoJobSource::class.java)
    }

    @Test
    fun `stops tracking a company, closing its live jobs at once so nobody is matched to them`() {
        jobs.saveAll(listOf(job(posting = posting("1")), job(posting = posting("2"))))

        val stopped = stop.stop(COMPANY.id!!)

        assertThat(stopped.tracked).isFalse()
        assertThat(companies.find(COMPANY.id!!)!!.untrackedAt).isEqualTo(now)
        assertThat(jobs.forCompany(COMPANY.id!!)).allMatch { it.inactiveSince == now }
    }

    @Test
    fun `stopping a company already untracked changes nothing`() {
        val earlier = now.minusSeconds(86_400)
        companies.save(COMPANY.untracked(earlier))

        assertThat(stop.stop(COMPANY.id!!).untrackedAt).isEqualTo(earlier)
    }

    @Test
    fun `crawls one tracked company now, rather than wait for tomorrow`() {
        crawlNow.crawl(COMPANY.id!!)

        assertThat(queue.queued).containsExactly(COMPANY.id)
    }

    @Test
    fun `does not crawl a company no longer tracked`() {
        companies.save(COMPANY.untracked(now))

        assertThatThrownBy { crawlNow.crawl(COMPANY.id!!) }.isInstanceOf(CompanyNotTracked::class.java)
        assertThat(queue.queued).isEmpty()
    }

    @Test
    fun `a company that does not exist cannot be stopped or crawled`() {
        assertThatThrownBy { stop.stop(99) }.isInstanceOf(TrackedCompanyNotFound::class.java)
        assertThatThrownBy { crawlNow.crawl(99) }.isInstanceOf(TrackedCompanyNotFound::class.java)
    }

    @Test
    fun `records a crawl that failed, and one parked after its last try, with why`() {
        recordFailure.record(COMPANY.id!!, "503 from the board", lastTry = false)
        assertThat(results.latest()[COMPANY.id]).isEqualTo(CrawlResult.failed(COMPANY.id!!, now, "503 from the board"))

        recordFailure.record(COMPANY.id!!, "503 from the board", lastTry = true)
        assertThat(results.latest()[COMPANY.id]).isEqualTo(CrawlResult.parked(COMPANY.id!!, now, "503 from the board"))
    }

    @Test
    fun `records a failure with a reason even when the error had no message`() {
        recordFailure.record(COMPANY.id!!, null, lastTry = false)

        assertThat(results.latest()[COMPANY.id]!!.error).isEqualTo("no reason given")
    }
}
