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
) {
    init {
        require(name.isNotBlank()) { "a profile needs a name" }
        require(experience.isNotBlank()) { "a profile needs experience" }
        require(education.isNotBlank()) { "a profile needs education" }
    }
}
