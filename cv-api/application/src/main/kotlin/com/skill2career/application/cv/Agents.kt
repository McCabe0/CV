package com.skill2career.application.cv

import com.skill2career.domain.cv.Cv
import com.skill2career.domain.cv.Question
import com.skill2career.domain.cv.Review
import com.skill2career.domain.cv.Round
import com.skill2career.domain.profile.Profile

// The agents, as driven ports. The adapters implement them with an LLM. See decisions/agents.md.

/** Finds what a strong CV needs but the profile does not say, as questions for the candidate. */
fun interface Interviewer {
    fun questionsFor(profile: Profile): List<Question>
}

/** Drafts the CV. After the first round, from the critic's last review too. */
fun interface Writer {
    fun write(brief: Brief): Cv
}

/** Scores a draft against the rubric, and says what to fix. */
fun interface Critic {
    fun review(
        brief: Brief,
        draft: Cv,
    ): Review
}

/** Something the candidate told the interviewer: as much a fact for the CV as the profile. */
data class Fact(
    val question: String,
    val answer: String,
)

/** Everything the writer and critic may use. Nothing else may appear on the CV. */
data class Brief(
    val profile: Profile,
    val facts: List<Fact>,
    val lastRound: Round? = null,
)

/**
 * An agent gave no usable answer, even after its retry. The request fails, as asking again will not
 * help. Any other error, eg the LLM being down, is let through so the message is retried later.
 */
class AgentFailure(
    message: String,
) : RuntimeException(message)
