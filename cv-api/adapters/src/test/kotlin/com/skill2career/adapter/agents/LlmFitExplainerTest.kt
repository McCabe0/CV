package com.skill2career.adapter.agents

import com.skill2career.application.cv.AgentFailure
import com.skill2career.domain.job.JobFixtures.job
import com.skill2career.domain.job.JobFixtures.posting
import com.skill2career.domain.job.ListedJob
import com.skill2career.domain.job.Workplace
import com.skill2career.domain.profile.ProfileFixtures.profile
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import kotlin.test.Test

class LlmFitExplainerTest {
    private val listed =
        ListedJob(
            job(
                id = 42,
                posting =
                    posting(
                        title = "Senior Backend Engineer",
                        location = "London or Remote (UK)",
                        description = "Build payment APIs in Kotlin and PostgreSQL.",
                        workplace = Workplace.REMOTE,
                    ),
            ),
            "Monzo",
        )

    @Test
    fun `gives the summary the LLM writes`() {
        val llm = ScriptedLlm("""{"summary": "Your Kotlin and PostgreSQL are what Monzo asks for."}""")

        assertThat(LlmFitExplainer(llm).explain(profile(), listed)).isEqualTo("Your Kotlin and PostgreSQL are what Monzo asks for.")
    }

    @Test
    fun `is shown the candidate's profile and the job, each in its own tags`() {
        val llm = ScriptedLlm("""{"summary": "It fits."}""")

        LlmFitExplainer(llm).explain(profile(), listed)

        val request = llm.requests.single()
        assertThat(request.prompt).contains("<profile>", "Casey Candidate", "Acme Payments")
        assertThat(request.prompt).contains(
            "<job>",
            "Senior Backend Engineer",
            "Monzo",
            "London or Remote (UK)",
            "remote",
            "Build payment APIs in Kotlin and PostgreSQL.",
            "</job>",
        )
        assertThat(request.schema["required"]).isEqualTo(listOf("summary"))
    }

    @Test
    fun `is told to use only what the profile says, to name gaps honestly, and to keep it short`() {
        val llm = ScriptedLlm("""{"summary": "It fits."}""")

        LlmFitExplainer(llm).explain(profile(), listed)

        assertThat(llm.requests.single().system).contains("only what the profile", "gap", "three sentences")
    }

    @Test
    fun `shows only the start of a very long description, as the rest rarely changes the answer`() {
        val long = "Kotlin. ".repeat(2_000)
        val llm = ScriptedLlm("""{"summary": "It fits."}""")

        LlmFitExplainer(llm).explain(profile(), listed.copy(job = listed.job.copy(posting = listed.job.posting.copy(description = long))))

        assertThat(
            llm.requests
                .single()
                .prompt.length,
        ).isLessThan(LlmFitExplainer.DESCRIPTION_CHARS + 2_000)
    }

    @Test
    fun `asks again for a summary that is blank or too long, then fails`() {
        val tooLong = "x".repeat(LlmFitExplainer.MAX_CHARS + 1)
        val llm = ScriptedLlm("""{"summary": " "}""", """{"summary": "$tooLong"}""")

        assertThatThrownBy { LlmFitExplainer(llm).explain(profile(), listed) }.isInstanceOf(AgentFailure::class.java)
        assertThat(llm.requests).hasSize(2)
    }
}
