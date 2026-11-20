package com.skill2career.domain.profile

/** A valid profile, shared by every module's tests, with each field changeable by name. */
object ProfileFixtures {
    val OWNER = OwnerId("candidate-sub")

    fun profile(
        id: Long? = null,
        ownerId: OwnerId = OWNER,
        name: String = "Casey Candidate",
        skills: List<String> = listOf("Kotlin", "Spring Boot", "PostgreSQL"),
        experience: String = "Five years building payment APIs.",
        education: String = "BSc Computer Science",
        targetRole: String? = "Backend Engineer",
        location: String? = "London",
        email: String? = "casey@example.com",
        workHistory: List<WorkExperience> =
            listOf(
                WorkExperience(
                    company = "Acme Payments",
                    title = "Software Engineer",
                    startDate = "2021-03",
                    endDate = null,
                    bullets = listOf("Cut settlement time by 40%"),
                ),
            ),
    ): Profile =
        Profile(
            id = id,
            ownerId = ownerId,
            name = name,
            skills = Skills(skills),
            experience = experience,
            education = education,
            targetRole = targetRole,
            location = location,
            contact = Contact(email = email),
            workHistory = workHistory,
        )
}
