package com.skill2career.application.job

import com.skill2career.domain.job.JobFixtures.COMPANY
import com.skill2career.domain.job.SourceType
import org.assertj.core.api.Assertions.assertThat
import java.time.LocalDate
import kotlin.test.Test

class ScheduleCrawlTest {
    private val today = LocalDate.parse("2026-11-23")
    private val companies = InMemoryTrackedCompanyStore(COMPANY)
    private val runs = InMemoryCrawlRuns()
    private val queue = RecordingCrawlQueue()
    private val schedule = ScheduleCrawl(companies, runs, queue)

    @Test
    fun `queues one crawl per tracked company, so each board is crawled on its own`() {
        val lever = companies.save(COMPANY.copy(id = null, name = "Spotify", source = SourceType.LEVER, boardToken = "spotify"))

        assertThat(schedule.schedule(today)).isTrue()

        assertThat(queue.queued).containsExactly(COMPANY.id, lever.id)
    }

    @Test
    fun `crawls once a day, however many workers ask`() {
        schedule.schedule(today)

        assertThat(schedule.schedule(today)).isFalse()

        assertThat(queue.queued).containsExactly(COMPANY.id)
    }

    @Test
    fun `crawls again the next day`() {
        schedule.schedule(today)

        assertThat(schedule.schedule(today.plusDays(1))).isTrue()

        assertThat(queue.queued).containsExactly(COMPANY.id, COMPANY.id)
    }

    @Test
    fun `queues nothing when no company is tracked`() {
        ScheduleCrawl(InMemoryTrackedCompanyStore(), runs, queue).schedule(today)

        assertThat(queue.queued).isEmpty()
    }
}
