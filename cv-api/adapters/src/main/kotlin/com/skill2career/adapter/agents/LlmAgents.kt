package com.skill2career.adapter.agents

import com.skill2career.adapter.llm.LlmClient
import com.skill2career.adapter.llm.LlmRequest
import com.skill2career.application.cv.Brief
import com.skill2career.application.cv.Critic
import com.skill2career.application.cv.Interviewer
import com.skill2career.application.cv.Writer
import com.skill2career.domain.cv.Cv
import com.skill2career.domain.cv.CvRequest
import com.skill2career.domain.cv.CvRole
import com.skill2career.domain.cv.Issue
import com.skill2career.domain.cv.IssueKind
import com.skill2career.domain.cv.Question
import com.skill2career.domain.cv.Review
import com.skill2career.domain.profile.Profile

// The agents, each an LLM with one job. The prompts are built each time from the profile, the answers
// and the last review, so there is no stored prompt to go stale. See decisions/agents.md.

class LlmInterviewer(
    llm: LlmClient,
) : Interviewer {
    private val call = AgentCall(llm)

    private data class Answer(
        val questions: List<Asked>,
    )

    private data class Asked(
        val question: String,
    )

    override fun questionsFor(profile: Profile): List<Question> =
        call.ask(
            "The interviewer",
            LlmRequest(SYSTEM, Prompts.profile(profile), SCHEMA),
            Answer::class.java,
        ) { answer -> answer.questions.mapIndexed { index, asked -> Question(index + 1, asked.question) } }

    private companion object {
        val SCHEMA = Schema.obj("questions" to Schema.array(Schema.obj("question" to Schema.string)))

        val SYSTEM =
            """
            You help a candidate get a strong CV for their target role. Read their profile and find what a
            strong CV needs that it does not say: a bullet with no number, a skill with no evidence of using
            it, a gap in the dates, a target role with no matching experience.
            Ask at most ${CvRequest.MAX_QUESTIONS} short questions, one thing each, that the candidate can
            answer in a sentence. Never ask for what the profile already says, or what the candidate has
            already answered. If they already say enough, ask none.
            """.trimIndent()
    }
}

class LlmWriter(
    llm: LlmClient,
) : Writer {
    private val call = AgentCall(llm)

    override fun write(brief: Brief): Cv =
        call.ask(
            "The writer",
            LlmRequest(
                SYSTEM,
                Prompts.profile(brief.profile) + (brief.lastRound?.let(Prompts::feedback) ?: ""),
                Schema.cv,
            ),
            CvAnswer::class.java,
        ) { it.toCv() }

    private companion object {
        val SYSTEM =
            """
            You write CVs. Use only what the profile and the candidate's answers say: never invent an
            employer, a title, a date, a number, a skill or a qualification.
            - Each bullet shows what came of the work, with a number where the facts give one.
            - Use the target role's keywords where the facts support them, for applicant tracking systems.
            - The summary is about this person and this role, in two or three sentences.
            - Leave a role's dates blank when they are not known.
            If an earlier draft and its review are given, write a new draft that fixes every issue raised.
            """.trimIndent()
    }
}

class LlmCritic(
    llm: LlmClient,
) : Critic {
    private val call = AgentCall(llm)

    private data class Answer(
        val score: Int,
        val approved: Boolean,
        val issues: List<Found>,
    )

    private data class Found(
        val kind: String,
        val detail: String,
    )

    override fun review(
        brief: Brief,
        draft: Cv,
    ): Review =
        call.ask(
            "The critic",
            LlmRequest(
                SYSTEM,
                Prompts.profile(brief.profile) + "<draft>\n${Prompts.draft(draft)}</draft>\n",
                SCHEMA,
            ),
            Answer::class.java,
        ) { answer ->
            Review(
                score = answer.score,
                approvedByCritic = answer.approved,
                issues =
                    answer.issues.map {
                        Issue(
                            IssueKind.entries.firstOrNull { k ->
                                k.name == it.kind
                            } ?: IssueKind.OTHER,
                            it.detail,
                        )
                    },
            )
        }

    private companion object {
        val SCHEMA =
            Schema.obj(
                "score" to Schema.integer,
                "approved" to Schema.boolean,
                "issues" to
                    Schema.array(
                        Schema.obj("kind" to Schema.oneOf(IssueKind.entries.map { it.name }), "detail" to Schema.string),
                    ),
            )

        val SYSTEM =
            """
            You check a draft CV against the candidate's profile and answers, which are the only facts it
            may use. Report each problem as an issue:
            - INVENTED_FACT: a claim found in neither the profile nor the answers. Check every employer,
              title, date, number, skill and qualification.
            - NO_IMPACT: a bullet that says what was done but not what came of it, where the facts say.
            - MISSING_KEYWORD: a keyword of the target role, supported by the facts, that the CV leaves out.
            - GENERIC_SUMMARY: a summary that could be about anyone.
            - OTHER: anything else that would stop it being sent.
            Score it from 0 to 100. Approve it only if it is ready to send, with no invented fact.
            """.trimIndent()
    }
}

/** The writer's draft, as json. Kept apart from the domain's Cv, so the schema can change on its own. */
internal data class CvAnswer(
    val summary: String,
    val skills: List<String>,
    val roles: List<RoleAnswer>,
    val education: List<String>,
    val projects: List<String>,
    val certifications: List<String>,
    val languages: List<String>,
) {
    fun toCv() =
        Cv(
            summary = summary,
            skills = skills,
            roles = roles.map { CvRole(it.title, it.company, it.dates.ifBlank { null }, it.bullets) },
            education = education,
            projects = projects,
            certifications = certifications,
            languages = languages,
        )
}

internal data class RoleAnswer(
    val title: String,
    val company: String,
    val dates: String,
    val bullets: List<String>,
)
