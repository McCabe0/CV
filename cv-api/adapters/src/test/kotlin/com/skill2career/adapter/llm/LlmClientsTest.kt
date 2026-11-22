package com.skill2career.adapter.llm

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import kotlin.test.Test

class LlmClientsTest {
    @Test
    fun `uses gemini by default`() {
        assertThat(LlmClients.create(LlmProperties(gemini = LlmProperties.Provider(apiKey = "key"))))
            .isInstanceOf(GeminiClient::class.java)
    }

    @Test
    fun `uses claude when asked`() {
        val properties = LlmProperties(provider = LlmProperties.Name.CLAUDE, claude = LlmProperties.Provider(apiKey = "key"))

        assertThat(LlmClients.create(properties)).isInstanceOf(ClaudeClient::class.java)
    }

    @Test
    fun `fails at the start without a key for the provider chosen, naming the setting`() {
        assertThatThrownBy { LlmClients.create(LlmProperties()) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("GEMINI_API_KEY")
        assertThatThrownBy { LlmClients.create(LlmProperties(provider = LlmProperties.Name.CLAUDE)) }
            .hasMessageContaining("ANTHROPIC_API_KEY")
    }

    @Test
    fun `the model and address can be set, eg for tests`() {
        val properties =
            LlmProperties(
                model = "gemini-other",
                gemini = LlmProperties.Provider(apiKey = "key", baseUrl = "http://localhost:9/v1beta"),
            )

        assertThat(LlmClients.geminiSettings(properties))
            .isEqualTo(GeminiSettings(apiKey = "key", model = "gemini-other", baseUrl = "http://localhost:9/v1beta"))
    }

    @Test
    fun `a blank model, as an unset environment variable gives, is the provider's default`() {
        val properties = LlmProperties(model = "", claude = LlmProperties.Provider(apiKey = "key"))

        assertThat(LlmClients.claudeSettings(properties).model).isEqualTo("claude-opus-5-5")
    }
}
