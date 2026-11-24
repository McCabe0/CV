package com.skill2career.adapter.messaging

import com.skill2career.application.job.CrawlCompany
import com.skill2career.application.job.RecordCrawlFailure
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.kafka.support.KafkaHeaders
import java.util.UUID
import kotlin.test.Test

class JobCrawlListenerTest {
    private val crawl = mock<CrawlCompany>()
    private val processed = mock<ProcessedEventRepo>()
    private val recordFailure = mock<RecordCrawlFailure>()
    private val listener = JobCrawlListener(crawl, processed, recordFailure)

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
    fun `records a failed crawl, and why, for the admin zone`() {
        whenever(crawl.crawl(7)) doThrow IllegalStateException("board down")

        runCatching { listener.onCrawl(message) }

        verify(recordFailure).record(7, "board down", lastTry = false)
    }

    /** The record kafka parks on the dlq: the message, with the last try's error in spring's header, as bytes. */
    private fun parkedRecord(error: String?) =
        ConsumerRecord("jobs.crawl.dlq", 0, 0L, "7", message).apply {
            if (error != null) headers().add(KafkaHeaders.EXCEPTION_MESSAGE, "Listener failed; $error".toByteArray())
        }

    @Test
    fun `a parked crawl leaves the company's jobs as they were, until the next day's, and is recorded as parked`() {
        listener.parked(parkedRecord("board down"))

        verify(crawl, never()).crawl(any())
        verify(recordFailure).record(7, "board down", lastTry = true)
    }

    @Test
    fun `a parked crawl without its error is still recorded as parked`() {
        listener.parked(parkedRecord(null))

        verify(recordFailure).record(7, null, lastTry = true)
    }

    @Test
    fun `a failure it cannot record still lets the crawl's own error through, so kafka retries it as it would`() {
        whenever(crawl.crawl(7)) doThrow IllegalStateException("board down")
        whenever(recordFailure.record(7, "board down", lastTry = false)) doThrow IllegalStateException("database down")

        assertThatThrownBy { listener.onCrawl(message) }.hasMessage("board down")
    }

    @Test
    fun `a parked crawl it cannot record is logged, not retried, eg its company was never there`() {
        whenever(recordFailure.record(7, "no tracked company 7", lastTry = true)) doThrow IllegalStateException("foreign key")

        listener.parked(parkedRecord("no tracked company 7"))
    }
}
