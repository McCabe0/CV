package com.skill2career.adapter.llm

/**
 * One call to an LLM, for json that fits a schema. The schema is plain json schema, as maps and lists,
 * the same for every provider.
 */
data class LlmRequest(
    val system: String,
    val prompt: String,
    val schema: Map<String, Any>,
)

/** An LLM provider. Under the agents only: the application never sees it. See decisions/agents.md. */
fun interface LlmClient {
    /** The model's answer: json text, meant to fit the request's schema. */
    fun complete(request: LlmRequest): String
}

/** The model answered, but not with something usable: it refused, or stopped short. Retrying the message will not help. */
class LlmUnusableAnswer(
    message: String,
) : RuntimeException(message)

/** The provider could not be used just now: rate limited, failing or unreachable. The message is retried later. */
class LlmUnavailable(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
