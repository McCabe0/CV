package com.skill2career.adapter.llm

import org.springframework.boot.context.properties.ConfigurationProperties

/** Which LLM the agents use: one provider and one model for all three. See decisions/agents.md. */
@ConfigurationProperties("llm")
data class LlmProperties(
    val provider: Name = Name.GEMINI,
    /** The model, for every agent. Unset: the provider's default. */
    val model: String? = null,
    val gemini: Provider = Provider(),
    val claude: Provider = Provider(),
) {
    enum class Name { GEMINI, CLAUDE }

    data class Provider(
        val apiKey: String? = null,
        /** Only to point at a stand in, eg wiremock in the tests. */
        val baseUrl: String? = null,
    )
}

object LlmClients {
    fun create(properties: LlmProperties): LlmClient =
        when (properties.provider) {
            LlmProperties.Name.GEMINI -> GeminiClient(geminiSettings(properties))
            LlmProperties.Name.CLAUDE -> ClaudeClient(claudeSettings(properties))
        }

    fun geminiSettings(properties: LlmProperties): GeminiSettings {
        val defaults = GeminiSettings(apiKey = key(properties.gemini, "gemini", "GEMINI_API_KEY"))
        return defaults.copy(
            model = properties.model?.ifBlank { null } ?: defaults.model,
            baseUrl = properties.gemini.baseUrl ?: defaults.baseUrl,
        )
    }

    fun claudeSettings(properties: LlmProperties): ClaudeSettings {
        val defaults = ClaudeSettings(apiKey = key(properties.claude, "claude", "ANTHROPIC_API_KEY"))
        return defaults.copy(
            model = properties.model?.ifBlank { null } ?: defaults.model,
            baseUrl = properties.claude.baseUrl ?: defaults.baseUrl,
        )
    }

    /** The cv-worker fails at its start without a key, rather than at its first CV. */
    private fun key(
        provider: LlmProperties.Provider,
        name: String,
        variable: String,
    ): String {
        val key = provider.apiKey
        check(!key.isNullOrBlank()) { "llm.provider is $name, so set $variable (llm.$name.api-key)" }
        return key
    }
}
