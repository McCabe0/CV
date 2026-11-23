package com.skill2career.adapter.messaging

import com.skill2career.application.job.FitSummaryNotFound
import com.skill2career.application.job.GiveUpOnFitSummary
import com.skill2career.application.job.WriteFitSummary
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.BackOff
import org.springframework.kafka.annotation.DltHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.RetryableTopic
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy
import org.springframework.stereotype.Component
import java.util.UUID

/** A summary for the cv-worker to write, on [Topics.JOBS_FIT]. Only its id, so the latest state is read. */
data class FitMessage(
    val eventId: UUID,
    val summaryId: Long,
) {
    fun write(): String = json.writeValueAsString(this)

    companion object {
        fun read(payload: String): FitMessage = json.readValue(payload, FitMessage::class.java)
    }
}

/**
 * The cv-worker writes each queued summary, as it holds the LLM. As with a CV request: an error is
 * logged and retried on `jobs.fit.retry` after a delay; after the last try the message is parked on
 * `jobs.fit.dlq` and the summary fails, so the candidate can ask again. A summary that does not exist goes
 * straight there. See decisions/messaging.md.
 */
@Component
class FitWorkListener(
    private val write: WriteFitSummary,
    private val processed: ProcessedEventRepo,
    private val giveUp: GiveUpOnFitSummary,
) {
    @RetryableTopic(
        attempts = "\${jobs.fit.attempts:3}",
        backOff = BackOff(delayString = "\${jobs.fit.retry-delay-ms:30000}"),
        retryTopicSuffix = ".retry",
        dltTopicSuffix = ".dlq",
        sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC,
        exclude = [FitSummaryNotFound::class],
    )
    @KafkaListener(topics = [Topics.JOBS_FIT], groupId = "fit-writer")
    fun onWork(payload: String) {
        val message = FitMessage.read(payload)
        if (processed.existsById(message.eventId)) return
        try {
            write.write(message.summaryId)
        } catch (failure: Exception) {
            log.warn("Fit summary {} could not be written, it will be retried: {}", message.summaryId, failure.message)
            throw failure
        }
        processed.save(ProcessedEventEntity(message.eventId))
    }

    /** Every try failed: the summary fails too, so the candidate can ask again later. */
    @DltHandler
    fun parked(payload: String) {
        val message = FitMessage.read(payload)
        log.error(
            "Fit summary {} parked on {}.dlq after its last try; it is failed for the candidate to ask again",
            message.summaryId,
            Topics.JOBS_FIT,
        )
        giveUp.giveUp(message.summaryId)
    }

    private companion object {
        val log = LoggerFactory.getLogger(FitWorkListener::class.java)
    }
}
