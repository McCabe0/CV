package com.skill2career.adapter.messaging

import com.skill2career.application.cv.AdvanceCvRequest
import com.skill2career.application.cv.CvRequestNotFound
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.slf4j.LoggerFactory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.kafka.annotation.BackOff
import org.springframework.kafka.annotation.DltHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.RetryableTopic
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/** An event a consumer has handled. */
@Entity
@Table(name = "processed_events")
class ProcessedEventEntity(
    @Id
    var eventId: UUID,
    var processedAt: Instant = Instant.now(),
)

interface ProcessedEventRepo : JpaRepository<ProcessedEventEntity, UUID>

/**
 * The cv-worker: takes each queued request as far as it can go. An error, eg the LLM being down, retries
 * the message on `cv.requested.retry`, after a delay, then parks it on `cv.requested.dlq`, where it can be
 * seen and replayed. A request that does not exist goes straight there: retrying will not make it.
 * See decisions/messaging.md.
 */
@Component
class CvWorkListener(
    private val advance: AdvanceCvRequest,
    private val processed: ProcessedEventRepo,
) {
    @RetryableTopic(
        attempts = "\${cv.worker.attempts:4}",
        backOff = BackOff(delayString = "\${cv.worker.retry-delay-ms:30000}"),
        retryTopicSuffix = ".retry",
        dltTopicSuffix = ".dlq",
        sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC,
        exclude = [CvRequestNotFound::class],
    )
    @KafkaListener(topics = [Topics.CV_REQUESTED], groupId = "cv-worker")
    fun onWork(payload: String) {
        val message = WorkMessage.read(payload)
        if (processed.existsById(message.eventId)) return
        advance.advance(message.requestId)
        processed.save(ProcessedEventEntity(message.eventId))
    }

    /** The request is left as it was, so replaying the message carries on from there. */
    @DltHandler
    fun parked(payload: String) {
        log.error("CV request work parked on {}.dlq, for someone to look at and replay: {}", Topics.CV_REQUESTED, payload)
    }

    private companion object {
        val log = LoggerFactory.getLogger(CvWorkListener::class.java)
    }
}
