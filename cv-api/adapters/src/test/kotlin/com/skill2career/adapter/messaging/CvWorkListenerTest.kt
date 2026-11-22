package com.skill2career.adapter.messaging

import com.skill2career.application.cv.AdvanceCvRequest
import com.skill2career.domain.cv.CvFixtures.writing
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

class CvWorkListenerTest {
    private val advance = mock<AdvanceCvRequest>()
    private val processed = mock<ProcessedEventRepo>()
    private val listener = CvWorkListener(advance, processed)

    private val event = UUID.randomUUID()
    private val message = WorkMessage(event, 42).write()

    @Test
    fun `advances the request the message names, then records the event as handled`() {
        whenever(advance.advance(42)) doReturn writing()

        listener.onWork(message)

        verify(advance).advance(42)
        verify(processed).save(any<ProcessedEventEntity>())
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
}
