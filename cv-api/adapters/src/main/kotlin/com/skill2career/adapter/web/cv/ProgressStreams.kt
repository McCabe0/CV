package com.skill2career.adapter.web.cv

import com.skill2career.application.cv.CvRequestStore
import com.skill2career.domain.cv.CvRequest
import com.skill2career.domain.cv.CvStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * The progress streams this api instance holds, by request. Each event is the whole request as it now
 * stands, so a browser that misses one loses nothing. The progress itself comes from kafka, as the
 * cv-worker that changes a request is another process. See decisions/messaging.md.
 */
@Component
class ProgressStreams internal constructor(
    private val requests: CvRequestStore,
    private val json: JsonMapper,
    private val newStream: () -> SseEmitter,
) {
    @Autowired
    constructor(requests: CvRequestStore, json: JsonMapper) : this(requests, json, { SseEmitter(TIMEOUT_MS) })

    internal constructor(requests: CvRequestStore, newStream: () -> SseEmitter) :
        this(requests, JsonMapper.builder().addModule(KotlinModule.Builder().build()).build(), newStream)

    private val open = ConcurrentHashMap<Long, MutableSet<SseEmitter>>()

    /** A stream for this request: its state now, then each change, until it is finished. */
    fun open(request: CvRequest): SseEmitter {
        val stream = newStream()
        val id = request.id!!
        if (!send(stream, request)) return stream
        if (request.finished) {
            stream.complete()
            return stream
        }
        open.computeIfAbsent(id) { CopyOnWriteArraySet() }.add(stream)
        stream.onCompletion { drop(id, stream) }
        stream.onTimeout { drop(id, stream) }
        stream.onError { drop(id, stream) }
        return stream
    }

    /** The request changed: its streams here get its new state. */
    fun changed(id: Long) {
        val streams = open[id] ?: return
        val request = requests.find(id) ?: return
        for (stream in streams) {
            if (send(stream, request) && request.finished) {
                stream.complete()
                drop(id, stream)
            }
        }
    }

    /** How many streams are open on this request, here. */
    fun openOn(id: Long): Int = open[id]?.size ?: 0

    private fun send(
        stream: SseEmitter,
        request: CvRequest,
    ): Boolean =
        try {
            stream.send(SseEmitter.event().name(EVENT).data(json.writeValueAsString(CvRequestResponse.of(request))))
            true
        } catch (gone: IOException) {
            drop(request.id!!, stream)
            false
        } catch (gone: IllegalStateException) {
            drop(request.id!!, stream)
            false
        }

    private fun drop(
        id: Long,
        stream: SseEmitter,
    ) {
        open.computeIfPresent(id) { _, streams -> streams.apply { remove(stream) }.ifEmpty { null } }
    }

    private val CvRequest.finished get() = status == CvStatus.COMPLETED || status == CvStatus.FAILED

    private companion object {
        const val EVENT = "progress"

        // A CV takes minutes, not hours. A browser that is still there reconnects.
        const val TIMEOUT_MS = 15 * 60 * 1000L
    }
}
