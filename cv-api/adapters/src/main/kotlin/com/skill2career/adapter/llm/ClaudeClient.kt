package com.skill2career.adapter.llm

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicException
import com.anthropic.models.beta.messages.BetaJsonOutputFormat
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams
import java.time.Duration

data class ClaudeSettings(
    val apiKey: String,
    val model: String = "claude-opus-5-5",
    val baseUrl: String = "https://api.anthropic.com",
    /** The sdk's own retries of a rate limit or a failure, before the message is retried. */
    val maxRetries: Int = 2,
    val timeout: Duration = Duration.ofMinutes(5),
)

/** Claude, through Anthropic's java sdk. */
class ClaudeClient(
    private val settings: ClaudeSettings,
) : LlmClient {
    private val client: AnthropicClient =
        AnthropicOkHttpClient
            .builder()
            .apiKey(settings.apiKey)
            .baseUrl(settings.baseUrl)
            .maxRetries(settings.maxRetries)
            .timeout(settings.timeout)
            .build()

    override fun complete(request: LlmRequest): String {
        val params =
            MessageCreateParams
                .builder()
                .model(settings.model)
                .maxTokens(MAX_TOKENS)
                .system(request.system)
                .addUserMessage(request.prompt)
                .outputConfig(
                    BetaOutputConfig
                        .builder()
                        .format(BetaJsonOutputFormat.builder().schema(schemaOf(request.schema)).build())
                        .build(),
                )
                // Should Claude decline, the api runs the same request on a fallback model of its choosing.
                .addBeta(FALLBACK_BETA)
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .build()

        val message =
            try {
                client.beta().messages().create(params)
            } catch (error: AnthropicException) {
                throw LlmUnavailable("Claude could not be used: ${error.message}", error)
            }

        when (message.stopReason().orElse(null)) {
            BetaStopReason.REFUSAL -> {
                throw LlmUnusableAnswer("Claude declined to answer")
            }

            BetaStopReason.MAX_TOKENS -> {
                throw LlmUnusableAnswer("Claude's answer was cut short")
            }

            else -> {}
        }
        return message.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")
    }

    private fun schemaOf(schema: Map<String, Any>): BetaJsonOutputFormat.Schema =
        BetaJsonOutputFormat.Schema
            .builder()
            .apply { schema.forEach { (key, value) -> putAdditionalProperty(key, JsonValue.from(value)) } }
            .build()

    private companion object {
        // A CV fits well within this, without streaming.
        const val MAX_TOKENS = 16_000L
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"
    }
}
