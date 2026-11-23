package com.skill2career.domain.job

import com.skill2career.domain.profile.ProfileFixtures.OWNER
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import kotlin.test.Test

class FitSummaryTest {
    private val asked = FitSummary(ownerId = OWNER, jobId = 42)

    @Test
    fun `a summary asked for is being written, with no text yet`() {
        assertThat(asked.status).isEqualTo(FitStatus.WRITING)
        assertThat(asked.text).isNull()
    }

    @Test
    fun `once written it is ready, with its text`() {
        val ready = asked.written("  Your Kotlin and Kafka are what it asks for.  ")

        assertThat(ready.status).isEqualTo(FitStatus.READY)
        assertThat(ready.text).isEqualTo("Your Kotlin and Kafka are what it asks for.")
    }

    @Test
    fun `a summary is never written blank`() {
        assertThatThrownBy { asked.written(" ") }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `a failed summary says why`() {
        val failed = asked.failed("The LLM is busy. Try again later.")

        assertThat(failed.status).isEqualTo(FitStatus.FAILED)
        assertThat(failed.failure).isEqualTo("The LLM is busy. Try again later.")
    }

    @Test
    fun `a failed summary can be asked for again, and is written afresh`() {
        val again = asked.failed("busy").askedAgain()

        assertThat(again.status).isEqualTo(FitStatus.WRITING)
        assertThat(again.failure).isNull()
    }

    @Test
    fun `only a failed summary is asked for again`() {
        assertThatThrownBy { asked.written("It fits.").askedAgain() }.isInstanceOf(IllegalStateException::class.java)
    }
}
