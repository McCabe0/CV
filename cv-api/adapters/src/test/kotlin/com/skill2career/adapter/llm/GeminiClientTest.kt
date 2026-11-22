package com.skill2career.adapter.llm

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThatThrownBy
import java.time.Duration
import kotlin.test.Test

/** Gemini's generateContent api. */
class GeminiClientTest : LlmClientContract() {
    private val path = "/v1beta/models/gemini-flash-latest:generateContent"

    override fun client(baseUrl: String): LlmClient =
        GeminiClient(
            GeminiSettings(
                apiKey = "test-key",
                model = "gemini-flash-latest",
                baseUrl = "$baseUrl/v1beta",
                timeout = Duration.ofSeconds(5),
            ),
        )

    private fun stub(body: String) {
        provider.stubFor(
            post(urlPathEqualTo(path)).willReturn(
                aResponse().withHeader("Content-Type", "application/json").withBody(body),
            ),
        )
    }

    private fun candidate(
        text: String,
        finishReason: String,
    ) = """{"candidates": [{"content": {"role": "model", "parts": [{"text": ${quoted(text)}}]}, "finishReason": "$finishReason"}]}"""

    private fun quoted(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    override fun answers(text: String) = stub(candidate(text, "STOP"))

    override fun refuses() = stub("""{"candidates": [{"finishReason": "SAFETY"}]}""")

    override fun cutsShort() = stub(candidate("""{"summary": "Backend""", "MAX_TOKENS"))

    override fun respondsWith(status: Int) {
        provider.stubFor(post(urlPathEqualTo(path)).willReturn(aResponse().withStatus(status)))
    }

    override fun assertSent(request: LlmRequest) {
        provider.verify(
            postRequestedFor(urlPathEqualTo(path))
                .withHeader("x-goog-api-key", equalTo("test-key"))
                .withRequestBody(
                    equalToJson(
                        """
                        {
                          "systemInstruction": {"parts": [{"text": "You write CVs."}]},
                          "contents": [{"role": "user", "parts": [{"text": "Write a CV for Casey."}]}],
                          "generationConfig": {
                            "responseMimeType": "application/json",
                            "responseJsonSchema": {"type": "object", "properties": {"summary": {"type": "string"}}}
                          }
                        }
                        """.trimIndent(),
                        true,
                        true,
                    ),
                ),
        )
    }

    @Test
    fun `a prompt blocked before any answer is unusable`() {
        stub("""{"promptFeedback": {"blockReason": "SAFETY"}}""")

        assertThatThrownBy { client(provider.baseUrl()).complete(request) }.isInstanceOf(LlmUnusableAnswer::class.java)
    }

    @Test
    fun `an answer split over several parts is joined`() {
        stub("""{"candidates": [{"content": {"parts": [{"text": "{\"summary\":"}, {"text": "\"Hi\"}"}]}, "finishReason": "STOP"}]}""")

        org.assertj.core.api.Assertions
            .assertThat(client(provider.baseUrl()).complete(request))
            .isEqualTo("""{"summary":"Hi"}""")
    }

    @Test
    fun `a response with no answer at all is unusable`() {
        stub("""{"candidates": []}""")

        assertThatThrownBy { client(provider.baseUrl()).complete(request) }
            .isInstanceOf(LlmUnusableAnswer::class.java)
            .hasMessage("Gemini gave no answer: no reason")
    }
}
