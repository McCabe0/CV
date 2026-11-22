package com.skill2career.domain.cv

import com.skill2career.domain.profile.ProfileFixtures

/** Valid CV pieces, shared by every module's tests, with each field changeable by name. */
object CvFixtures {
    fun cv(
        summary: String = "Backend engineer who cut settlement time by 40% at Acme Payments.",
        skills: List<String> = listOf("Kotlin", "Spring Boot", "PostgreSQL"),
    ): Cv =
        Cv(
            summary = summary,
            skills = skills,
            roles =
                listOf(
                    CvRole(
                        title = "Software Engineer",
                        company = "Acme Payments",
                        dates = "2021 – present",
                        bullets = listOf("Cut settlement time by 40%"),
                    ),
                ),
            education = listOf("BSc Computer Science"),
        )

    fun review(
        score: Int = 90,
        approved: Boolean = true,
        issues: List<Issue> = emptyList(),
    ): Review = Review(score = score, approvedByCritic = approved, issues = issues)

    fun rejected(
        score: Int = 60,
        vararg issues: Issue = arrayOf(Issue(IssueKind.NO_IMPACT, "Bullets lack numbers")),
    ): Review = review(score = score, approved = false, issues = issues.toList())

    fun question(
        id: Int = 1,
        text: String = "By how much did you cut settlement time?",
    ) = Question(id, text)

    /** A request that has been asked its questions, and is waiting for the answers. */
    fun awaitingAnswers(vararg questions: Question = arrayOf(question())): CvRequest =
        CvRequest.requested(ProfileFixtures.OWNER).copy(id = 1).asked(questions.toList())

    /** A request ready for the writer: no questions were needed. */
    fun writing(): CvRequest = CvRequest.requested(ProfileFixtures.OWNER).copy(id = 1).asked(emptyList())
}
