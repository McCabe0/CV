package com.skill2career.adapter.agents

import com.skill2career.adapter.llm.LlmClient
import com.skill2career.adapter.llm.LlmRequest
import com.skill2career.adapter.llm.LlmUnusableAnswer
import com.skill2career.application.cv.AgentFailure
import tools.jackson.core.JacksonException
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

/**
 * One question to an LLM, for an answer of type [A], made into a [T]. An answer that is unusable (a
 * refusal, json that does not fit, or a value the domain refuses) is asked for once more, then the agent
 * fails. An unavailable provider is let through, so the message is retried later. See decisions/agents.md.
 */
internal class AgentCall(
    private val llm: LlmClient,
) {
    fun <A, T> ask(
        agent: String,
        request: LlmRequest,
        answer: Class<A>,
        toDomain: (A) -> T,
    ): T {
        var problem = ""
        repeat(ATTEMPTS) {
            try {
                return toDomain(json.readValue(llm.complete(request), answer))
            } catch (unusable: LlmUnusableAnswer) {
                problem = unusable.message.orEmpty()
            } catch (unreadable: JacksonException) {
                problem = "its answer did not fit the schema: ${unreadable.originalMessage}"
            } catch (refused: IllegalArgumentException) {
                problem = "its answer broke a rule: ${refused.message}"
            }
        }
        throw AgentFailure("$agent gave no usable answer: $problem")
    }

    private companion object {
        const val ATTEMPTS = 2

        val json: JsonMapper =
            JsonMapper
                .builder()
                .addModule(KotlinModule.Builder().build())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build()
    }
}
