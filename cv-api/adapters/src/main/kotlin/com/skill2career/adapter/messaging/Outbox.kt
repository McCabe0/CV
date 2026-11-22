package com.skill2career.adapter.messaging

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.slf4j.LoggerFactory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/** The topics, as decisions/messaging.md lists them. */
object Topics {
    /** A CV request for the cv-worker to take as far as it can. Keyed by the request, so its messages stay in order. */
    const val CV_REQUESTED = "cv.requested"

    /** A CV request changed: every api instance passes it to the progress streams it holds. */
    const val CV_PROGRESS = "cv.progress"
}

private val json = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

/**
 * A message about one CV request, on [Topics.CV_REQUESTED] or [Topics.CV_PROGRESS]. Only its id: a
 * consumer reads the request itself, so it always acts on its latest state. The event id lets a consumer
 * tell a message delivered twice.
 */
data class RequestMessage(
    val eventId: UUID,
    val requestId: Long,
) {
    fun write(): String = json.writeValueAsString(this)

    companion object {
        fun read(payload: String): RequestMessage = json.readValue(payload, RequestMessage::class.java)
    }
}

/** A message waiting to be sent, saved in the same transaction as the change it announces. */
@Entity
@Table(name = "outbox")
class OutboxEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var eventId: UUID,
    var topic: String,
    var messageKey: String,
    var payload: String,
    var createdAt: Instant = Instant.now(),
    var sentAt: Instant? = null,
) {
    companion object {
        fun work(message: RequestMessage) = of(Topics.CV_REQUESTED, message)

        fun progress(message: RequestMessage) = of(Topics.CV_PROGRESS, message)

        private fun of(
            topic: String,
            message: RequestMessage,
        ) = OutboxEntity(
            eventId = message.eventId,
            topic = topic,
            messageKey = message.requestId.toString(),
            payload = message.write(),
        )
    }
}

interface OutboxRepo : JpaRepository<OutboxEntity, Long> {
    /** The oldest unsent messages, locked, skipping any another instance's relay holds, so none is sent twice at once. */
    @Query(
        value = "SELECT * FROM {h-schema}outbox WHERE sent_at IS NULL ORDER BY id LIMIT :limit FOR UPDATE SKIP LOCKED",
        nativeQuery = true,
    )
    fun nextUnsent(limit: Int): List<OutboxEntity>
}

/**
 * Sends the outbox to kafka, in the order it was written. A message kafka refuses stops the run, so it
 * and those after it go next time, in order. Delivery is at least once: should the transaction fail after
 * a send, the message goes again, so every consumer handles a message delivered twice.
 */
@Component
@Transactional
class OutboxRelay(
    private val outbox: OutboxRepo,
    private val kafka: KafkaTemplate<String, String>,
) {
    fun relay(batch: Int = BATCH): Int {
        var sent = 0
        for (message in outbox.nextUnsent(batch)) {
            try {
                kafka.send(message.topic, message.messageKey, message.payload).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            } catch (failure: Exception) {
                log.warn("Kafka refused outbox message {}, it will be sent on the next run: {}", message.eventId, failure.message)
                break
            }
            message.sentAt = Instant.now()
            sent++
        }
        return sent
    }

    private companion object {
        const val BATCH = 100
        const val SEND_TIMEOUT_SECONDS = 10L
        val log = LoggerFactory.getLogger(OutboxRelay::class.java)
    }
}
