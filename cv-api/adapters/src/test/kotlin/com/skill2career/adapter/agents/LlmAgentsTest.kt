package com.skill2career.adapter.agents

import com.skill2career.application.cv.Brief
import com.skill2career.domain.cv.CvFixtures.cv
import com.skill2career.domain.cv.CvFixtures.rejected
import com.skill2career.domain.cv.CvRole
import com.skill2career.domain.cv.Issue
import com.skill2career.domain.cv.IssueKind
import com.skill2career.domain.cv.Question
import com.skill2career.domain.cv.Review
import com.skill2career.domain.cv.Round
import com.skill2career.domain.profile.Fact
import com.skill2career.domain.profile.ProfileFixtures.profile
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

class LlmAgentsTest {
    private val answered = profile().withFacts(listOf(Fact("By how much did you cut settlement time?", "By 40%")))
    private val brief = Brief(answered)

    private val cvJson =
        """
        {
          "summary": "Backend engineer.",
          "skills": ["Kotlin", "SQL"],
          "roles": [{"title": "Engineer", "company": "Acme", "dates": "", "bullets": ["Cut time by 40%"]}],
          "education": ["BSc"], "projects": [], "certifications": [], "languages": ["English"]
        }
        """.trimIndent()

    @Test
    fun `the interviewer's questions are numbered from one`() {
        val llm = ScriptedLlm("""{"questions": [{"question": "By how much?"}, {"question": "Which team?"}]}""")

        val questions = LlmInterviewer(llm).questionsFor(profile())

        assertThat(questions).containsExactly(Question(1, "By how much?"), Question(2, "Which team?"))
    }

    @Test
    fun `the interviewer is shown the profile, and asked for at most five questions`() {
        val llm = ScriptedLlm("""{"questions": []}""")

        LlmInterviewer(llm).questionsFor(profile())

        val request = llm.requests.single()
        assertThat(request.prompt).contains("Casey Candidate", "Backend Engineer", "Acme Payments", "Cut settlement time by 40%")
        assertThat(request.system).contains("at most 5")
        assertThat(request.schema["required"]).isEqualTo(listOf("questions"))
    }

    @Test
    fun `the interviewer is shown the answers already given, so it does not ask again`() {
        val llm = ScriptedLlm("""{"questions": []}""")

        LlmInterviewer(llm).questionsFor(answered)

        val request = llm.requests.single()
        assertThat(request.prompt).contains("By how much did you cut settlement time?", "By 40%")
        assertThat(request.system).contains("already answered")
    }

    @Test
    fun `the writer's draft becomes the CV, a blank date being none`() {
        val draft = LlmWriter(ScriptedLlm(cvJson)).write(brief)

        assertThat(draft.summary).isEqualTo("Backend engineer.")
        assertThat(draft.roles).containsExactly(CvRole("Engineer", "Acme", null, listOf("Cut time by 40%")))
        assertThat(draft.languages).containsExactly("English")
    }

    @Test
    fun `the writer is given the profile and the candidate's answers, and told never to invent`() {
        val llm = ScriptedLlm(cvJson)

        LlmWriter(llm).write(brief)

        val request = llm.requests.single()
        assertThat(request.prompt).contains("Casey Candidate", "By how much did you cut settlement time?", "By 40%")
        assertThat(request.prompt).doesNotContain("Earlier draft")
        assertThat(request.system).contains("never invent")
    }

    @Test
    fun `after the first round, the writer is given its last draft and the critic's review`() {
        val llm = ScriptedLlm(cvJson)
        val lastRound =
            Round(
                1,
                cv(summary = "A vague summary."),
                rejected(65, Issue(IssueKind.GENERIC_SUMMARY, "Name the payments work")),
            )

        LlmWriter(llm).write(brief.copy(lastRound = lastRound))

        assertThat(
            llm.requests.single().prompt,
        ).contains("Earlier draft", "A vague summary.", "65", "GENERIC_SUMMARY", "Name the payments work")
    }

    @Test
    fun `the critic's review becomes a review`() {
        val llm =
            ScriptedLlm(
                """{"score": 72, "approved": false, "issues": [{"kind": "NO_IMPACT", "detail": "Add numbers"}]}""",
            )

        val review = LlmCritic(llm).review(brief, cv())

        assertThat(review).isEqualTo(Review(72, false, listOf(Issue(IssueKind.NO_IMPACT, "Add numbers"))))
    }

    @Test
    fun `the critic is shown the draft beside the facts it may use, and the rubric`() {
        val llm = ScriptedLlm("""{"score": 90, "approved": true, "issues": []}""")

        LlmCritic(llm).review(brief, cv(summary = "The draft to check."))

        val request = llm.requests.single()
        assertThat(request.prompt).contains("The draft to check.", "Casey Candidate", "By 40%")
        assertThat(request.system).contains("INVENTED_FACT", "NO_IMPACT", "MISSING_KEYWORD", "GENERIC_SUMMARY")
    }

    @Test
    fun `a score out of range is an unusable answer, so the critic is asked again`() {
        val llm =
            ScriptedLlm(
                """{"score": 140, "approved": true, "issues": []}""",
                """{"score": 88, "approved": true, "issues": []}""",
            )

        assertThat(LlmCritic(llm).review(brief, cv()).score).isEqualTo(88)
    }

    @Test
    fun `an issue of a kind the critic should not use is kept, as other`() {
        val llm = ScriptedLlm("""{"score": 70, "approved": false, "issues": [{"kind": "TYPO", "detail": "Teh"}]}""")

        assertThat(LlmCritic(llm).review(brief, cv()).issues).containsExactly(Issue(IssueKind.OTHER, "Teh"))
    }

    @Test
    fun `a role's dates are kept when the writer gives them`() {
        val llm = ScriptedLlm(cvJson.replace("\"dates\": \"\"", "\"dates\": \"2021 – now\""))

        assertThat(
            LlmWriter(llm)
                .write(brief)
                .roles
                .single()
                .dates,
        ).isEqualTo("2021 – now")
    }
}
