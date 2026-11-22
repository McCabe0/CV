package com.skill2career.adapter.llm

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.github.tomakehurst.wiremock.http.Fault
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test

/**
 * What every LLM client must do, whichever provider it calls. Each provider's test says how its api
 * looks on the wire, with wiremock playing it, and inherits these tests. So changing provider is a
 * tested change of setting. See decisions/agents.md.
 */
abstract class LlmClientContract {
    protected val provider = WireMockServer(options().dynamicPort())

    protected val request =
        LlmRequest(
            system = "You write CVs.",
            prompt = "Write a CV for Casey.",
            schema = mapOf("type" to "object", "properties" to mapOf("summary" to mapOf("type" to "string"))),
        )

    /** The client under test, calling the provider at this url, with no retries of its own. */
    abstract fun client(baseUrl: String): LlmClient

    /** The provider answers with this text as the model's output. */
    abstract fun answers(text: String)

    /** The provider declines to answer, for its own safety reasons. */
    abstract fun refuses()

    /** The provider stops before the answer is finished, eg at its token limit. */
    abstract fun cutsShort()

    /** The provider answers with this http status. */
    abstract fun respondsWith(status: Int)

    /** The provider was sent the request's system instruction, prompt and schema, in its own format. */
    abstract fun assertSent(request: LlmRequest)

    @BeforeEach
    fun start() = provider.start()

    @AfterEach
    fun stop() = provider.stop()

    private fun complete() = client(provider.baseUrl()).complete(request)

    @Test
    fun `returns the model's answer`() {
        answers("""{"summary":"Backend engineer."}""")

        assertThat(complete()).isEqualTo("""{"summary":"Backend engineer."}""")
    }

    @Test
    fun `sends the system instruction, the prompt and the schema`() {
        answers("{}")

        complete()

        assertSent(request)
    }

    @Test
    fun `a refusal is an unusable answer, not one to retry`() {
        refuses()

        assertThatThrownBy { complete() }.isInstanceOf(LlmUnusableAnswer::class.java)
    }

    @Test
    fun `an answer cut short is unusable`() {
        cutsShort()

        assertThatThrownBy { complete() }.isInstanceOf(LlmUnusableAnswer::class.java)
    }

    @Test
    fun `is unavailable when rate limited, so the work is retried later`() {
        respondsWith(429)

        assertThatThrownBy { complete() }.isInstanceOf(LlmUnavailable::class.java)
    }

    @Test
    fun `is unavailable when the provider fails`() {
        respondsWith(503)

        assertThatThrownBy { complete() }.isInstanceOf(LlmUnavailable::class.java)
    }

    @Test
    fun `is unavailable when the provider cannot be reached`() {
        provider.stubFor(
            com.github.tomakehurst.wiremock.client.WireMock
                .any(
                    com.github.tomakehurst.wiremock.client.WireMock
                        .anyUrl(),
                ).willReturn(
                    com.github.tomakehurst.wiremock.client.WireMock
                        .aResponse()
                        .withFault(Fault.CONNECTION_RESET_BY_PEER),
                ),
        )

        assertThatThrownBy { complete() }.isInstanceOf(LlmUnavailable::class.java)
    }
}
