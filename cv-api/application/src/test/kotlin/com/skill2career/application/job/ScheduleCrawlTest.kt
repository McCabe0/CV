package com.skill2career.application.job

import com.skill2career.domain.job.JobFixtures.COMPANY
import com.skill2career.domain.job.SourceType
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

class ScheduleCrawlTest {
    @Test
    fun `queues one crawl per tracked company, so each board is crawled on its own`() {
        val companies = InMemoryTrackedCompanyStore(COMPANY)
        val lever = companies.save(COMPANY.copy(id = null, name = "Spotify", source = SourceType.LEVER, boardToken = "spotify"))
        val queue = RecordingCrawlQueue()

        ScheduleCrawl(companies, queue).schedule()

        assertThat(queue.queued).containsExactly(COMPANY.id, lever.id)
    }

    @Test
    fun `queues nothing when no company is tracked`() {
        val queue = RecordingCrawlQueue()

        ScheduleCrawl(InMemoryTrackedCompanyStore(), queue).schedule()

        assertThat(queue.queued).isEmpty()
    }
}
