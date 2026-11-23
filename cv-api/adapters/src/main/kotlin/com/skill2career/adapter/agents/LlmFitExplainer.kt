package com.skill2career.adapter.agents

import com.skill2career.adapter.llm.LlmClient
import com.skill2career.adapter.llm.LlmRequest
import com.skill2career.application.job.FitExplainer
import com.skill2career.domain.job.ListedJob
import com.skill2career.domain.profile.Profile

/**
 * Why a job fits a candidate, as an LLM says it: their profile against the job. The job's words are
 * marked off in tags as the profile's are, so a posting is read as facts about the job, never as
 * instructions. See decisions/jobs.md.
 */
class LlmFitExplainer(
    llm: LlmClient,
) : FitExplainer {
    private val call = AgentCall(llm)

    private data class Answer(
        val summary: String,
    )

    override fun explain(
        profile: Profile,
        job: ListedJob,
    ): String =
        call.ask(
            "The fit summary",
            LlmRequest(SYSTEM, Prompts.profile(profile) + job(job), SCHEMA),
            Answer::class.java,
        ) { answer ->
            val summary = answer.summary.trim()
            require(summary.isNotEmpty()) { "the summary was blank" }
            require(summary.length <= MAX_CHARS) { "the summary was longer than $MAX_CHARS characters" }
            summary
        }

    private fun job(listed: ListedJob): String {
        val posting = listed.job.posting
        return buildString {
            appendLine()
            appendLine("<job>")
            appendLine("Title: ${posting.title}")
            appendLine("Company: ${listed.company}")
            posting.location?.let { appendLine("Location: $it") }
            posting.workplace?.let { appendLine("Workplace: ${it.name.lowercase()}") }
            appendLine("Description:")
            appendLine(posting.description.take(DESCRIPTION_CHARS))
            append("</job>")
        }
    }

    companion object {
        /** Enough for any posting's requirements; the rest is usually benefits and legal text. */
        const val DESCRIPTION_CHARS = 6_000

        const val MAX_CHARS = 800

        private val SCHEMA = Schema.obj("summary" to Schema.string)

        private val SYSTEM =
            """
            You tell a candidate why a job fits them, so they can decide whether to apply. Read their profile
            and the job. In at most three sentences, plainly, say which of their experience and skills the job
            asks for. If the job asks for something important they do not show, name that gap honestly.
            Use only what the profile and the job say: never invent experience, skills or numbers.
            Speak to the candidate as "you".
            """.trimIndent()
    }
}
