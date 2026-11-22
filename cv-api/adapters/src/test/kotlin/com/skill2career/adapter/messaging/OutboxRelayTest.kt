package com.skill2career.adapter.messaging

import com.skill2career.adapter.persistence.PostgresTest
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.Test

class OutboxRelayTest : PostgresTest() {
    @Autowired
    lateinit var outbox: OutboxRepo

    private val kafka = mock<KafkaTemplate<String, String>>()
    private val relay by lazy { OutboxRelay(outbox, kafka) }

    private fun queue(requestId: Long): OutboxEntity = outbox.save(OutboxEntity.work(RequestMessage(UUID.randomUUID(), requestId)))

    private fun kafkaAccepts() {
        whenever(kafka.send(any<String>(), any<String>(), any<String>())) doReturn
            CompletableFuture.completedFuture(mock<SendResult<String, String>>())
    }

    @Test
    fun `sends each queued message to its topic, keyed by its request, and marks it sent`() {
        kafkaAccepts()
        val queued = queue(42)

        assertThat(relay.relay()).isEqualTo(1)

        verify(kafka).send(Topics.CV_REQUESTED, "42", queued.payload)
        assertThat(outbox.findById(queued.id!!).get().sentAt).isNotNull()
    }

    @Test
    fun `sends a message once`() {
        kafkaAccepts()
        queue(42)

        relay.relay()

        assertThat(relay.relay()).isZero()
        verify(kafka).send(any<String>(), any<String>(), any<String>())
    }

    @Test
    fun `sends in the order queued`() {
        kafkaAccepts()
        val first = queue(1)
        val second = queue(2)

        relay.relay()

        val order = org.mockito.Mockito.inOrder(kafka)
        order.verify(kafka).send(Topics.CV_REQUESTED, "1", first.payload)
        order.verify(kafka).send(Topics.CV_REQUESTED, "2", second.payload)
    }

    @Test
    fun `stops at a message kafka refuses, leaving it and those after it for the next run`() {
        val first = queue(1)
        val second = queue(2)
        whenever(kafka.send(eq(Topics.CV_REQUESTED), eq("1"), any<String>())) doReturn
            CompletableFuture.failedFuture(IllegalStateException("broker down"))

        assertThat(relay.relay()).isZero()

        verify(kafka, never()).send(eq(Topics.CV_REQUESTED), eq("2"), any<String>())
        assertThat(outbox.findById(first.id!!).get().sentAt).isNull()
        assertThat(outbox.findById(second.id!!).get().sentAt).isNull()
    }
}
