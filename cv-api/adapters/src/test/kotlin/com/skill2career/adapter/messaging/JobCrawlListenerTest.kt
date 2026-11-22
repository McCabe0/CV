package com.skill2career.adapter.messaging

import com.skill2career.application.job.CrawlCompany
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID
import kotlin.test.Test

class JobCrawlListenerTest {
    private val crawl = mock<CrawlCompany>()
    private val processed = mock<ProcessedEventRepo>()
    private val listener = JobCrawlListener(crawl, processed)

    private val event = UUID.randomUUID()
    private val message = CrawlMessage(event, 7).write()

    @Test
    fun `crawls the company the message names, then records the event as handled`() {
        listener.onCrawl(message)

        verify(crawl).crawl(7)
        verify(processed).save(any<ProcessedEventEntity>())
    }

    @Test
    fun `skips an event it has already handled, as kafka can deliver one twice`() {
        whenever(processed.existsById(event)) doReturn true

        listener.onCrawl(message)

        verify(crawl, never()).crawl(any())
    }

    @Test
    fun `lets a failure through, so kafka retries the crawl, and records nothing`() {
        whenever(crawl.crawl(7)) doThrow IllegalStateException("board down")

        assertThatThrownBy { listener.onCrawl(message) }.isInstanceOf(IllegalStateException::class.java)

        verify(processed, never()).save(any<ProcessedEventEntity>())
    }

    @Test
    fun `a parked crawl leaves the company's jobs as they were, until the next day's`() {
        listener.parked(message)

        verify(crawl, never()).crawl(any())
    }
}
