package com.skill2career.adapter.agents

import com.skill2career.application.cv.Fact
import com.skill2career.domain.cv.Cv
import com.skill2career.domain.cv.CvRole
import com.skill2career.domain.profile.Contact
import com.skill2career.domain.profile.OwnerId
import com.skill2career.domain.profile.Profile
import com.skill2career.domain.profile.Skills
import com.skill2career.domain.profile.WorkExperience
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

class PromptsTest {
    private val full =
        Profile(
            ownerId = OwnerId("o"),
            name = "Casey",
            skills = Skills(listOf("Kotlin", "SQL")),
            experience = "Five years.",
            education = "BSc",
            targetRole = "Backend Engineer",
            yearsOfExperience = "5",
            location = "Dublin",
            workAuthorization = "EU citizen",
            projects = listOf("CV writer", "Job matcher"),
            certifications = listOf("AWS Developer"),
            languages = listOf("English", "Irish"),
            contact = Contact(email = "casey@example.com"),
            workHistory =
                listOf(
                    WorkExperience("Acme", "Engineer", startDate = "2021-03", endDate = null, bullets = listOf("Cut time by 40%")),
                    WorkExperience("Initech", "Intern", startDate = "2019-06", endDate = "2019-09"),
                    WorkExperience("Globex", "Volunteer"),
                ),
        )

    @Test
    fun `the profile shows everything given, in tags, so it is read as facts`() {
        val text = Prompts.profile(full)

        assertThat(text).startsWith("<profile>").endsWith("</profile>\n")
        assertThat(text).contains(
            "Name: Casey",
            "Target role: Backend Engineer",
            "Years of experience: 5",
            "Location: Dublin",
            "Right to work: EU citizen",
            "Skills: Kotlin, SQL",
            "Role: Engineer, Acme, 2021-03 to now",
            "  - Cut time by 40%",
            "Role: Intern, Initech, 2019-06 to 2019-09",
            "Role: Volunteer, Globex\n",
            "Projects: CV writer; Job matcher",
            "Certifications: AWS Developer",
            "Languages: English; Irish",
        )
    }

    @Test
    fun `the profile leaves out what was not given, and never the contact details`() {
        val text = Prompts.profile(full.copy(targetRole = null, location = " ", projects = emptyList()))

        assertThat(text).doesNotContain("Target role", "Location", "Projects", "casey@example.com")
    }

    @Test
    fun `answers are only shown when there are some`() {
        assertThat(Prompts.facts(emptyList())).isEmpty()
        assertThat(Prompts.facts(listOf(Fact("Which team?", "Payments")))).isEqualTo("<answers>\nQ: Which team?\nA: Payments\n</answers>\n")
    }

    @Test
    fun `a draft shows each of its sections`() {
        val text =
            Prompts.draft(
                Cv(
                    summary = "Engineer.",
                    skills = listOf("Kotlin"),
                    roles = listOf(CvRole("Engineer", "Acme", "2021 – now", listOf("Did a thing")), CvRole("Intern", "Initech")),
                    education = listOf("BSc"),
                    projects = listOf("CV writer"),
                    certifications = listOf("AWS"),
                    languages = listOf("English"),
                ),
            )

        assertThat(text).contains(
            "Summary: Engineer.",
            "Role: Engineer, Acme, 2021 – now\n  - Did a thing",
            "Role: Intern, Initech\n",
            "Education: BSc",
            "Project: CV writer",
            "Certification: AWS",
            "Languages: English",
        )
        assertThat(Prompts.draft(Cv(summary = "S.", skills = listOf("K")))).doesNotContain("Languages")
    }
}
