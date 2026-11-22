package com.skill2career.adapter.agents

import com.skill2career.adapter.llm.LlmClient
import com.skill2career.adapter.llm.LlmRequest

/** An LLM that gives these answers in turn, an exception being thrown, and keeps every request. */
class ScriptedLlm(
    vararg answers: Any,
) : LlmClient {
    private val next = answers.iterator()
    val requests = mutableListOf<LlmRequest>()

    override fun complete(request: LlmRequest): String {
        requests += request
        return when (val answer = next.next()) {
            is Throwable -> throw answer
            else -> answer.toString()
        }
    }
}
