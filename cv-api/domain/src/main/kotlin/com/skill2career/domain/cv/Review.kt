package com.skill2career.domain.cv

/** What the critic checks for. See decisions/agents.md. */
enum class IssueKind {
    /** A claim that is in neither the profile nor the answers. */
    INVENTED_FACT,

    /** A bullet that says what was done, not what came of it. */
    NO_IMPACT,

    /** A keyword of the target role that applicant tracking systems will look for. */
    MISSING_KEYWORD,

    /** A summary that could be about anyone. */
    GENERIC_SUMMARY,
    OTHER,
}

data class Issue(
    val kind: IssueKind,
    val detail: String,
)

/** The critic's verdict on one draft. */
data class Review(
    val score: Int,
    val approvedByCritic: Boolean,
    val issues: List<Issue> = emptyList(),
) {
    init {
        require(score in 0..100) { "a score is out of 100" }
    }

    /** A CV must never say what the candidate did not, so an invented fact always blocks approval. */
    val approved: Boolean get() = approvedByCritic && issues.none { it.kind == IssueKind.INVENTED_FACT }
}

/** One turn of the loop: the writer's draft and the critic's review of it. */
data class Round(
    val number: Int,
    val draft: Cv,
    val review: Review,
)
