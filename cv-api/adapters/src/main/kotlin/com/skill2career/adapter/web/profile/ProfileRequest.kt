package com.skill2career.adapter.web.profile

import com.skill2career.domain.profile.Contact
import com.skill2career.domain.profile.OwnerId
import com.skill2career.domain.profile.Profile
import com.skill2career.domain.profile.Skills
import com.skill2career.domain.profile.WorkExperience
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

private const val WEB_URL = "^https?://\\S+$"

@Schema(description = "A role the candidate has held.")
data class WorkExperienceRequest(
    @field:NotBlank @field:Size(max = 100)
    val company: String,
    @field:NotBlank @field:Size(max = 100)
    val title: String,
    @field:Size(max = 20)
    @field:Schema(example = "2021-03")
    val startDate: String? = null,
    @field:Size(max = 20)
    @field:Schema(description = "Empty while the role is current.")
    val endDate: String? = null,
    @field:Size(max = 20)
    val bullets: List<
        @NotBlank
        @Size(max = 500)
        String,
    > = emptyList(),
)

@Schema(description = "What the candidate tells us about themselves. The CV is written only from this.")
data class ProfileRequest(
    @field:NotBlank @field:Size(max = 100)
    @field:Schema(example = "Casey Candidate")
    val name: String,
    @field:NotEmpty @field:Size(max = 50)
    @field:Schema(description = "Each once; case and spaces are ignored.", example = "[\"Kotlin\", \"SQL\"]")
    val skills: List<
        @NotBlank
        @Size(max = 50)
        String,
    >,
    @field:NotBlank @field:Size(max = 5000)
    val experience: String,
    @field:NotBlank @field:Size(max = 2000)
    val education: String,
    @field:Size(max = 100)
    val targetRole: String? = null,
    @field:Size(max = 20)
    val yearsOfExperience: String? = null,
    @field:Size(max = 100)
    val location: String? = null,
    @field:Size(max = 100)
    val workAuthorization: String? = null,
    @field:Size(max = 20)
    val projects: List<
        @NotBlank
        @Size(max = 500)
        String,
    > = emptyList(),
    @field:Size(max = 20)
    val certifications: List<
        @NotBlank
        @Size(max = 200)
        String,
    > = emptyList(),
    @field:Size(max = 20)
    val languages: List<
        @NotBlank
        @Size(max = 50)
        String,
    > = emptyList(),
    @field:Email @field:Size(max = 254)
    val email: String? = null,
    @field:Size(max = 30)
    val phone: String? = null,
    @field:Pattern(regexp = WEB_URL) @field:Size(max = 300)
    val linkedin: String? = null,
    @field:Pattern(regexp = WEB_URL) @field:Size(max = 300)
    val portfolio: String? = null,
    @field:Size(max = 30)
    val workHistory: List<
        @Valid
        WorkExperienceRequest,
    > = emptyList(),
) {
    fun toProfile(ownerId: OwnerId): Profile =
        Profile(
            ownerId = ownerId,
            name = name.trim(),
            skills = Skills(skills),
            experience = experience,
            education = education,
            targetRole = targetRole,
            yearsOfExperience = yearsOfExperience,
            location = location,
            workAuthorization = workAuthorization,
            projects = projects,
            certifications = certifications,
            languages = languages,
            contact = Contact(email = email, phone = phone, linkedin = linkedin, portfolio = portfolio),
            workHistory =
                workHistory.map {
                    WorkExperience(
                        company = it.company,
                        title = it.title,
                        startDate = it.startDate,
                        endDate = it.endDate,
                        bullets = it.bullets,
                    )
                },
        )
}
