package com.skill2career.adapter.web.profile

object ProfileRequestFixtures {
    fun work(
        company: String = "Acme Payments",
        title: String = "Software Engineer",
    ): WorkExperienceRequest =
        WorkExperienceRequest(
            company = company,
            title = title,
            startDate = "2021-03",
            endDate = null,
            bullets = listOf("Cut settlement time by 40%"),
        )

    fun request(
        name: String = "Casey Candidate",
        skills: List<String> = listOf("Kotlin", "Spring Boot", "PostgreSQL"),
        experience: String = "Five years building payment APIs.",
        education: String = "BSc Computer Science",
        email: String? = "casey@example.com",
        linkedin: String? = "https://www.linkedin.com/in/casey",
        workHistory: List<WorkExperienceRequest> = listOf(work()),
    ): ProfileRequest =
        ProfileRequest(
            name = name,
            skills = skills,
            experience = experience,
            education = education,
            targetRole = "Backend Engineer",
            yearsOfExperience = "5",
            location = "London",
            workAuthorization = "UK citizen",
            projects = listOf("Open source payments SDK"),
            certifications = emptyList(),
            languages = listOf("English"),
            email = email,
            phone = "07700900123",
            linkedin = linkedin,
            portfolio = null,
            workHistory = workHistory,
        )
}
