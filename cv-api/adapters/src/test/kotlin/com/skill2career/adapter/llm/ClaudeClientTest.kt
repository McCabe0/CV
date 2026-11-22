package com.skill2career.adapter.llm

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import java.time.Duration

/** Claude's messages api, as its sdk calls it. */
class ClaudeClientTest : LlmClientContract() {
    override fun client(baseUrl: String): LlmClient =
        ClaudeClient(
            ClaudeSettings(
                apiKey = "test-key",
                model = "claude-opus-5-5",
                baseUrl = baseUrl,
                maxRetries = 0,
                timeout = Duration.ofSeconds(5),
            ),
        )

    private fun message(
        text: String,
        stopReason: String,
    ) = """
        {
          "id": "msg_1", "type": "message", "role": "assistant", "model": "claude-opus-5-5",
          "content": [{"type": "text", "text": ${quoted(text)}}],
          "stop_reason": "$stopReason", "stop_sequence": null,
          "usage": {"input_tokens": 10, "output_tokens": 5}
        }
        """.trimIndent()

    private fun quoted(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun stub(body: String) {
        provider.stubFor(
            post(urlPathEqualTo("/v1/messages")).willReturn(
                aResponse().withHeader("Content-Type", "application/json").withBody(body),
            ),
        )
    }

    override fun answers(text: String) = stub(message(text, "end_turn"))

    override fun refuses() = stub(message("", "refusal"))

    override fun cutsShort() = stub(message("""{"summary": "Backend""", "max_tokens"))

    override fun respondsWith(status: Int) {
        provider.stubFor(
            post(urlPathEqualTo("/v1/messages")).willReturn(
                aResponse()
                    .withStatus(status)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""{"type":"error","error":{"type":"overloaded_error","message":"busy"}}"""),
            ),
        )
    }

    override fun assertSent(request: LlmRequest) {
        provider.verify(
            postRequestedFor(urlPathEqualTo("/v1/messages"))
                .withHeader("Authorization", equalTo("Bearer test-key"))
                // On a refusal, the api tries again on a fallback model it chooses. See decisions/agents.md.
                .withHeader("anthropic-beta", equalTo("server-side-fallback-2026-07-01"))
                .withRequestBody(
                    equalToJson(
                        """
                        {
                          "model": "claude-opus-5-5",
                          "system": "You write CVs.",
                          "messages": [{"role": "user", "content": "Write a CV for Casey."}],
                          "output_config": {
                            "format": {
                              "type": "json_schema",
                              "schema": {"type": "object", "properties": {"summary": {"type": "string"}}}
                            }
                          },
                          "fallbacks": "default"
                        }
                        """.trimIndent(),
                        true,
                        true,
                    ),
                ),
        )
    }
}
