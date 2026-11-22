package com.skill2career.domain.profile

/** Who a profile belongs to: their keycloak subject. */
@JvmInline
value class OwnerId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "an owner needs an id" }
    }
}

data class Contact(
    val email: String? = null,
    val phone: String? = null,
    val linkedin: String? = null,
    val portfolio: String? = null,
)

data class WorkExperience(
    val company: String,
    val title: String,
    val startDate: String? = null,
    val endDate: String? = null,
    val bullets: List<String> = emptyList(),
)

/** Something the candidate told the interviewer: as much a fact for their CVs as the rest of the profile. */
data class Fact(
    val question: String,
    val answer: String,
) {
    init {
        require(question.isNotBlank()) { "a fact needs its question" }
        require(answer.isNotBlank()) { "a fact needs an answer" }
    }
}

/** The same question, however it is spaced or cased. Outside the class, so it is never saved with it. */
private val Fact.key: String get() = question.trim().lowercase()

/** What a candidate tells us about themselves. Everything the CV agents may say comes from here. */
data class Profile(
    val id: Long? = null,
    val ownerId: OwnerId,
    val name: String,
    val skills: Skills,
    val experience: String,
    val education: String,
    val targetRole: String? = null,
    val yearsOfExperience: String? = null,
    val location: String? = null,
    val workAuthorization: String? = null,
    val projects: List<String> = emptyList(),
    val certifications: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val contact: Contact = Contact(),
    val workHistory: List<WorkExperience> = emptyList(),
    /** The interviewer's answers, kept so a later CV uses them and the same question is not asked again. */
    val facts: List<Fact> = emptyList(),
) {
    init {
        require(name.isNotBlank()) { "a profile needs a name" }
        require(experience.isNotBlank()) { "a profile needs experience" }
        require(education.isNotBlank()) { "a profile needs education" }
    }

    /** Adds the candidate's new answers. A newer answer to a question replaces the older, in its place. */
    fun withFacts(new: List<Fact>): Profile {
        val newer = new.associateBy { it.key }
        val kept = facts.map { newer[it.key] ?: it }
        return copy(facts = kept + new.filter { fact -> kept.none { it.key == fact.key } })
    }
}
