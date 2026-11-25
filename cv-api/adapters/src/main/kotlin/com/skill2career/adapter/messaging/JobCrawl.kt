package com.skill2career.adapter.messaging

import com.skill2career.adapter.jobboard.UnknownJobBoard
import com.skill2career.application.job.CrawlCompany
import com.skill2career.application.job.CrawlQueue
import com.skill2career.application.job.CrawlRuns
import com.skill2career.application.job.NoJobSource
import com.skill2career.application.job.RecordCrawlFailure
import com.skill2career.application.job.ScheduleCrawl
import com.skill2career.application.job.TrackedCompanyNotFound
import com.skill2career.domain.job.TrackedCompany
import jakarta.persistence.EntityManager
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.BackOff
import org.springframework.kafka.annotation.DltHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.RetryableTopic
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** A crawl of one company's board, on [Topics.JOBS_CRAWL]. Only the company's id: the worker reads the rest. */
data class CrawlMessage(
    val eventId: UUID,
    val companyId: Long,
) {
    fun write(): String = json.writeValueAsString(this)

    companion object {
        fun read(payload: String): CrawlMessage = json.readValue(payload, CrawlMessage::class.java)
    }
}

/** The [CrawlQueue] port, on the outbox: the crawl is sent to kafka once the transaction queueing it commits. */
@Repository
@Transactional
class OutboxCrawlQueue(
    private val outbox: OutboxRepo,
) : CrawlQueue {
    override fun queue(company: TrackedCompany) {
        outbox.save(OutboxEntity.crawl(CrawlMessage(UUID.randomUUID(), company.id!!)))
    }
}

/** The [CrawlRuns] port: a row per day, its date the primary key, so postgres lets only one claim it. */
@Repository
@Transactional
class PostgresCrawlRuns(
    private val entityManager: EntityManager,
) : CrawlRuns {
    override fun claim(day: LocalDate): Boolean =
        entityManager
            .createNativeQuery("INSERT INTO {h-schema}crawl_runs (run_date) VALUES (:day) ON CONFLICT DO NOTHING")
            .setParameter("day", day)
            .executeUpdate() == 1
}

/**
 * Asks for today's crawl, as every job-worker does each hour: the first to ask each day queues it. The
 * claim and the queued crawls are one transaction. Days are UTC, so every worker agrees on what today is.
 */
@Component
@Transactional
class CrawlScheduler(
    private val schedule: ScheduleCrawl,
    private val clock: Clock,
) {
    fun run() {
        val today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC)
        if (schedule.schedule(today)) log.info("Queued the crawl of every tracked company's board for {}", today)
    }

    private companion object {
        val log = LoggerFactory.getLogger(CrawlScheduler::class.java)
    }
}

/**
 * The job-worker: crawls each queued company's board. A failure, eg the board being down, is logged,
 * recorded for the admin zone, and retries the message on `jobs.crawl.retry`, after a delay. After the
 * last try it is parked on `jobs.crawl.dlq`, recorded as parked, and the company's jobs stay as they were
 * until the next day's crawl. A board or company that does not exist goes straight there: retrying will
 * not make it. See decisions/messaging.md.
 */
@Component
class JobCrawlListener(
    private val crawl: CrawlCompany,
    private val processed: ProcessedEventRepo,
    private val recordFailure: RecordCrawlFailure,
) {
    @RetryableTopic(
        attempts = "\${jobs.crawl.attempts:3}",
        backOff = BackOff(delayString = "\${jobs.crawl.retry-delay-ms:60000}"),
        retryTopicSuffix = ".retry",
        dltTopicSuffix = ".dlq",
        sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC,
        exclude = [UnknownJobBoard::class, TrackedCompanyNotFound::class, NoJobSource::class],
    )
    @KafkaListener(topics = [Topics.JOBS_CRAWL], groupId = "job-worker")
    fun onCrawl(payload: String) {
        val message = CrawlMessage.read(payload)
        if (processed.existsById(message.eventId)) return
        try {
            crawl.crawl(message.companyId)
        } catch (failure: Exception) {
            log.warn("Company {}'s board could not be crawled, it will be retried: {}", message.companyId, failure.message)
            tryToRecord(message.companyId, failure.message, lastTry = false)
            throw failure
        }
        processed.save(ProcessedEventEntity(message.eventId))
    }

    /**
     * Every try failed. The last try's error is on the parked record, in spring's exception header, as
     * bytes, after "Listener failed; ".
     */
    @DltHandler
    fun parked(record: ConsumerRecord<String, String>) {
        val message = CrawlMessage.read(record.value())
        val error =
            record
                .headers()
                .lastHeader(KafkaHeaders.EXCEPTION_MESSAGE)
                ?.value()
                ?.toString(Charsets.UTF_8)
                ?.removePrefix(LISTENER_FAILED)
        log.error(
            "Company {}'s crawl parked on {}.dlq after its last try; its jobs stay as they were until tomorrow's crawl: {}",
            message.companyId,
            Topics.JOBS_CRAWL,
            error,
        )
        tryToRecord(message.companyId, error, lastTry = true)
    }

    /** Records the failure, if it can: failing to record it must not change how the crawl is retried. */
    private fun tryToRecord(
        companyId: Long,
        error: String?,
        lastTry: Boolean,
    ) {
        try {
            recordFailure.record(companyId, error, lastTry)
        } catch (unrecorded: Exception) {
            log.warn("Company {}'s failed crawl could not be recorded: {}", companyId, unrecorded.message)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(JobCrawlListener::class.java)

        /** What spring puts before the crawl's own error, on the parked record. */
        const val LISTENER_FAILED = "Listener failed; "
    }
}
