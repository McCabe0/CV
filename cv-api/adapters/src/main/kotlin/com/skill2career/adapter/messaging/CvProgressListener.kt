package com.skill2career.adapter.messaging

import com.skill2career.adapter.web.cv.ProgressStreams
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * Every api instance reads every progress message, in a consumer group of its own, as the instance
 * holding a candidate's stream is not the one that knows of the change. Only new messages: a stream
 * opened later gets the request's state when it opens. See decisions/messaging.md.
 */
@Component
class CvProgressListener(
    private val streams: ProgressStreams,
) {
    @KafkaListener(
        topics = [Topics.CV_PROGRESS],
        groupId = "cv-progress-#{T(java.util.UUID).randomUUID()}",
        properties = ["auto.offset.reset=latest"],
    )
    fun onProgress(payload: String) {
        streams.changed(RequestMessage.read(payload).requestId)
    }
}
