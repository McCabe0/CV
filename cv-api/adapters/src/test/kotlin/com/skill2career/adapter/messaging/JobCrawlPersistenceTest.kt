package com.skill2career.adapter.messaging

import com.skill2career.adapter.persistence.PostgresTest
import com.skill2career.domain.job.JobFixtures.COMPANY
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import java.time.LocalDate
import kotlin.test.Test

@Import(OutboxCrawlQueue::class, PostgresCrawlRuns::class)
class JobCrawlPersistenceTest : PostgresTest() {
    @Autowired
    lateinit var queue: OutboxCrawlQueue

    @Autowired
    lateinit var runs: PostgresCrawlRuns

    @Autowired
    lateinit var outbox: OutboxRepo

    @Test
    fun `queues a company's crawl in the outbox, keyed by the company`() {
        queue.queue(COMPANY)

        val queued = outbox.findAll().single()
        assertThat(queued.topic).isEqualTo(Topics.JOBS_CRAWL)
        assertThat(queued.messageKey).isEqualTo(COMPANY.id.toString())
        assertThat(CrawlMessage.read(queued.payload)).isEqualTo(CrawlMessage(queued.eventId, COMPANY.id!!))
    }

    @Test
    fun `claims a day once`() {
        val day = LocalDate.parse("2026-11-23")

        assertThat(runs.claim(day)).isTrue()
        assertThat(runs.claim(day)).isFalse()
        assertThat(runs.claim(day.plusDays(1))).isTrue()
    }
}
