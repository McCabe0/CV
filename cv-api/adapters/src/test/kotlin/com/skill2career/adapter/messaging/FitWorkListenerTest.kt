package com.skill2career.adapter.messaging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.skill2career.application.job.GiveUpOnFitSummary
import com.skill2career.application.job.WriteFitSummary
import com.skill2career.domain.job.FitSummary
import com.skill2career.domain.profile.ProfileFixtures.OWNER
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.test.Test

class FitWorkListenerTest {
    private val write = mock<WriteFitSummary>()
    private val processed = mock<ProcessedEventRepo>()
    private val giveUp = mock<GiveUpOnFitSummary>()
    private val listener = FitWorkListener(write, processed, giveUp)

    private val event = UUID.randomUUID()
    private val message = FitMessage(event, 42).write()

    private val logged =
        ListAppender<ILoggingEvent>().apply {
            start()
            (LoggerFactory.getLogger(FitWorkListener::class.java) as Logger).addAppender(this)
        }

    @AfterEach
    fun stopListening() {
        (LoggerFactory.getLogger(FitWorkListener::class.java) as Logger).detachAppender(logged)
    }

    @Test
    fun `writes the summary the message names, then records the event as handled`() {
        whenever(write.write(42)) doReturn FitSummary(id = 42, ownerId = OWNER, jobId = 7).written("It fits.")

        listener.onWork(message)

        verify(write).write(42)
        verify(processed).save(any<ProcessedEventEntity>())
    }

    @Test
    fun `skips an event it has already handled, as kafka can deliver one twice`() {
        whenever(processed.existsById(event)) doReturn true

        listener.onWork(message)

        verify(write, never()).write(any())
    }

    @Test
    fun `records nothing and logs why when the writing fails, so the retry does it again`() {
        whenever(write.write(42)) doThrow IllegalStateException("Gemini could not be used: 503")

        assertThatThrownBy { listener.onWork(message) }.hasMessage("Gemini could not be used: 503")

        verify(processed, never()).save(any<ProcessedEventEntity>())
        assertThat(logged.list.single().formattedMessage).contains("summary 42", "503", "retried")
    }

    @Test
    fun `gives the summary up once its work is parked, so the candidate can ask again`() {
        listener.parked(message)

        verify(giveUp).giveUp(42)
        assertThat(logged.list.single().level).isEqualTo(Level.ERROR)
    }
}
