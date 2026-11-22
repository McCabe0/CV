package com.skill2career.adapter.web.cv

import com.skill2career.application.cv.InMemoryCvRequestStore
import com.skill2career.domain.cv.CvFixtures.awaitingAnswers
import com.skill2career.domain.cv.CvFixtures.cv
import com.skill2career.domain.cv.CvFixtures.review
import com.skill2career.domain.cv.CvFixtures.writing
import com.skill2career.domain.cv.CvRequest
import com.skill2career.domain.cv.Round
import org.assertj.core.api.Assertions.assertThat
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import kotlin.test.Test

class ProgressStreamsTest {
    /** A stream to a browser that keeps what it is sent, until sending fails as [failure] says. */
    private class Browser(
        var failure: Exception? = null,
    ) : SseEmitter() {
        val sent = mutableListOf<String>()
        var ended = false

        constructor(gone: Boolean) : this(if (gone) IOException("the browser closed the page") else null)

        override fun send(builder: SseEventBuilder) {
            failure?.let { throw it }
            sent += builder.build().joinToString("") { (it as ResponseBodyEmitter.DataWithMediaType).data.toString() }
        }

        override fun complete() {
            ended = true
        }
    }

    private val requests = InMemoryCvRequestStore()
    private val browsers = ArrayDeque<Browser>()
    private val streams = ProgressStreams(requests) { browsers.removeFirst() }

    private fun opened(
        request: CvRequest,
        browser: Browser = Browser(),
    ): Browser {
        browsers += browser
        streams.open(request)
        return browser
    }

    @Test
    fun `sends the request as it stands as soon as the stream opens`() {
        val request = requests.save(writing().copy(id = null))

        val browser = opened(request)

        assertThat(browser.sent.single()).contains("event:progress", "\"status\":\"WRITING\"")
        assertThat(browser.ended).isFalse()
    }

    @Test
    fun `sends each change to every stream open on that request, and to no other`() {
        val request = requests.save(writing().copy(id = null))
        val other = requests.save(writing().copy(id = null))
        val first = opened(request)
        val second = opened(request)
        val watchingOther = opened(other)
        requests.save(request.withRound(Round(1, cv(summary = "Draft one."), review(score = 64, approved = false))))

        streams.changed(request.id!!)

        assertThat(first.sent).hasSize(2)
        assertThat(first.sent.last()).contains("\"score\":64", "Draft one.")
        assertThat(second.sent).hasSize(2)
        assertThat(watchingOther.sent).hasSize(1)
    }

    @Test
    fun `ends the stream once the request is finished, as nothing more will come`() {
        val request = requests.save(writing().copy(id = null))
        val browser = opened(request)
        requests.save(request.withRound(Round(1, cv(), review())).completed())

        streams.changed(request.id!!)

        assertThat(browser.sent.last()).contains("\"status\":\"COMPLETED\"")
        assertThat(browser.ended).isTrue()
        assertThat(streams.openOn(request.id!!)).isZero()
    }

    @Test
    fun `a stream opened on a finished request gets its state, then ends`() {
        val failed = requests.save(CvRequest.requested(awaitingAnswers().ownerId).failed("The interviewer failed"))

        val browser = opened(failed)

        assertThat(browser.sent.single()).contains("\"status\":\"FAILED\"", "The interviewer failed")
        assertThat(browser.ended).isTrue()
        assertThat(streams.openOn(failed.id!!)).isZero()
    }

    @Test
    fun `drops a stream whose browser has gone`() {
        val request = requests.save(writing().copy(id = null))
        val gone = Browser(gone = true)

        opened(request, gone)

        assertThat(streams.openOn(request.id!!)).isZero()
    }

    @Test
    fun `ignores a change to a request nobody is watching`() {
        streams.changed(999)

        assertThat(streams.openOn(999)).isZero()
    }

    @Test
    fun `drops a stream whose browser goes while it is open, keeping the others`() {
        val request = requests.save(writing().copy(id = null))
        val leaving = opened(request)
        val staying = opened(request)
        leaving.failure = IOException("the browser closed the page")

        streams.changed(request.id!!)

        assertThat(streams.openOn(request.id!!)).isEqualTo(1)
        assertThat(staying.sent).hasSize(2)
    }

    @Test
    fun `drops a stream already ended by the server, eg on its timeout`() {
        val request = requests.save(writing().copy(id = null))
        val timedOut = opened(request)
        timedOut.failure = IllegalStateException("ResponseBodyEmitter has already completed")

        streams.changed(request.id!!)

        assertThat(streams.openOn(request.id!!)).isZero()
    }

    @Test
    fun `keeps a stream open when the request is gone, sending nothing`() {
        val store = org.mockito.kotlin.mock<com.skill2career.application.cv.CvRequestStore>()
        val browser = Browser()
        val watching = ProgressStreams(store) { browser }
        watching.open(writing())

        watching.changed(writing().id!!)

        assertThat(browser.sent).hasSize(1)
        assertThat(watching.openOn(writing().id!!)).isEqualTo(1)
    }
}
