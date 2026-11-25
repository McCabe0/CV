package com.skill2career.adapter.messaging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.skill2career.application.cv.AdvanceCvRequest
import com.skill2career.application.cv.GiveUpOnCvRequest
import com.skill2career.domain.cv.CvFixtures.awaitingAnswers
import com.skill2career.domain.cv.CvFixtures.cv
import com.skill2career.domain.cv.CvFixtures.question
import com.skill2career.domain.cv.CvFixtures.rejected
import com.skill2career.domain.cv.CvFixtures.review
import com.skill2career.domain.cv.CvFixtures.writing
import com.skill2career.domain.cv.Round
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

class CvWorkListenerTest {
    private val advance = mock<AdvanceCvRequest>()
    private val processed = mock<ProcessedEventRepo>()
    private val giveUp = mock<GiveUpOnCvRequest>()
    private val listener = CvWorkListener(advance, processed, giveUp)

    private val event = UUID.randomUUID()
    private val message = RequestMessage(event, 42).write()

    /** What the listener logs, kept to check. */
    private val logged =
        ListAppender<ILoggingEvent>().apply {
            start()
            (LoggerFactory.getLogger(CvWorkListener::class.java) as Logger).addAppender(this)
        }

    @AfterEach
    fun stopListening() {
        (LoggerFactory.getLogger(CvWorkListener::class.java) as Logger).detachAppender(logged)
    }

    @Test
    fun `advances the request the message names, then records the event as handled`() {
        whenever(advance.advance(42)) doReturn writing()

        listener.onWork(message)

        verify(advance).advance(42)
        verify(processed).save(any<ProcessedEventEntity>())
    }

    @Test
    fun `logs that the request is waiting for answers, and to how many questions`() {
        whenever(advance.advance(42)) doReturn awaitingAnswers(question(1), question(2, "Which cloud?"))

        listener.onWork(message)

        val info = logged.list.single()
        assertThat(info.level).isEqualTo(Level.INFO)
        assertThat(info.formattedMessage).contains("CV request 42", "AWAITING_ANSWERS", "2 questions")
    }

    @Test
    fun `logs that the request is written, with its rounds and the last score, so a slow CV can be followed`() {
        val written =
            writing()
                .withRound(Round(1, cv(), rejected(score = 70)))
                .withRound(Round(2, cv(), review(score = 92)))
                .completed()
        whenever(advance.advance(42)) doReturn written

        listener.onWork(message)

        assertThat(logged.list.single().formattedMessage)
            .contains("CV request 42", "COMPLETED", "2 rounds", "last scored 92", "approved")
    }

    @Test
    fun `skips an event it has already handled, as kafka can deliver one twice`() {
        whenever(processed.existsById(event)) doReturn true

        listener.onWork(message)

        verify(advance, never()).advance(any())
    }

    @Test
    fun `records nothing when the work fails, so the retry does it again`() {
        whenever(advance.advance(42)) doThrow IllegalStateException("gemini is down")

        assertThatThrownBy { listener.onWork(message) }.hasMessage("gemini is down")

        verify(processed, never()).save(any<ProcessedEventEntity>())
    }

    @Test
    fun `logs why the work failed, so it can be seen without reading kafka's headers`() {
        whenever(advance.advance(42)) doThrow IllegalStateException("Gemini could not be used: 503 high demand")

        runCatching { listener.onWork(message) }

        val warning = logged.list.single()
        assertThat(warning.level).isEqualTo(Level.WARN)
        assertThat(warning.formattedMessage)
            .contains("CV request 42")
            .contains("Gemini could not be used: 503 high demand")
            .contains("retried")
    }

    @Test
    fun `gives the request up once its work is parked, so the candidate is not left waiting`() {
        listener.parked(message)

        verify(giveUp).giveUp(42)
        assertThat(logged.list.single().level).isEqualTo(Level.ERROR)
    }
}
