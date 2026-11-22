package com.skill2career.adapter.messaging

import com.skill2career.adapter.web.cv.ProgressStreams
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.UUID
import kotlin.test.Test

class CvProgressListenerTest {
    @Test
    fun `passes each change to the streams this instance holds`() {
        val streams = mock<ProgressStreams>()

        CvProgressListener(streams).onProgress(RequestMessage(UUID.randomUUID(), 42).write())

        verify(streams).changed(42)
    }
}
