package com.skill2career.domain.cv

/** A role on the CV, as the writer phrased it. */
data class CvRole(
    val title: String,
    val company: String,
    val dates: String? = null,
    val bullets: List<String> = emptyList(),
)

/** The CV itself: structured, so it can be shown, checked and turned into a pdf the same way. */
data class Cv(
    val summary: String,
    val skills: List<String>,
    val roles: List<CvRole> = emptyList(),
    val education: List<String> = emptyList(),
    val projects: List<String> = emptyList(),
    val certifications: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
) {
    init {
        require(summary.isNotBlank()) { "a CV needs a summary" }
        require(skills.isNotEmpty()) { "a CV needs at least one skill" }
    }
}
