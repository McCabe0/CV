package com.skill2career.adapter.messaging

import com.skill2career.application.cv.AdvanceCvRequest
import com.skill2career.application.cv.CvRequestNotFound
import com.skill2career.application.cv.GiveUpOnCvRequest
import com.skill2career.domain.cv.CvRequest
import com.skill2career.domain.cv.CvStatus
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
 * The cv-worker: takes each queued request as far as it can go. An error, eg the LLM being down, is
 * logged, and retries the message on `cv.requested.retry`, after a delay. After the last try the message
 * is parked on `cv.requested.dlq`, where it can be seen, and the request fails, so the candidate is told
 * to try again rather than left waiting. A request that does not exist goes straight there: retrying will
 * not make it. See decisions/messaging.md.
 */
@Component
class CvWorkListener(
    private val advance: AdvanceCvRequest,
    private val processed: ProcessedEventRepo,
    private val giveUp: GiveUpOnCvRequest,
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
        val message = RequestMessage.read(payload)
        if (processed.existsById(message.eventId)) return
        val request =
            try {
                advance.advance(message.requestId)
            } catch (failure: Exception) {
                log.warn("CV request {} could not be advanced, it will be retried: {}", message.requestId, failure.message)
                throw failure
            }
        processed.save(ProcessedEventEntity(message.eventId))
        log.info("CV request {} is {}{}", message.requestId, request.status, progress(request))
    }

    /** How far the agents got: the questions waiting for answers, or the rounds and the last score. */
    private fun progress(request: CvRequest): String {
        val last = request.rounds.lastOrNull()
        return when {
            request.status == CvStatus.AWAITING_ANSWERS -> {
                ", with ${counted(request.questions.size, "question")}"
            }

            last != null -> {
                ", after ${counted(request.rounds.size, "round")}, the last scored ${last.review.score}, " +
                    if (last.review.approvedByCritic) "approved" else "not approved"
            }

            else -> {
                ""
            }
        }
    }

    private fun counted(
        count: Int,
        thing: String,
    ) = if (count == 1) "1 $thing" else "$count ${thing}s"

    /** Every try failed: the request fails too, with a reason the candidate can act on. */
    @DltHandler
    fun parked(payload: String) {
        val message = RequestMessage.read(payload)
        log.error(
            "CV request {} parked on {}.dlq after its last try; it is failed for the candidate to ask again",
            message.requestId,
            Topics.CV_REQUESTED,
        )
        giveUp.giveUp(message.requestId)
    }

    private companion object {
        val log = LoggerFactory.getLogger(CvWorkListener::class.java)
    }
}
