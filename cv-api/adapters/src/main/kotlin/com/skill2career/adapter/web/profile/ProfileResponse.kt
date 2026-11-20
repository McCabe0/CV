package com.skill2career.adapter.web.profile

import com.skill2career.domain.profile.Profile
import com.skill2career.domain.profile.WorkExperience

data class ProfileResponse(
    val id: Long,
    val name: String,
    val skills: List<String>,
    val experience: String,
    val education: String,
    val targetRole: String?,
    val yearsOfExperience: String?,
    val location: String?,
    val workAuthorization: String?,
    val projects: List<String>,
    val certifications: List<String>,
    val languages: List<String>,
    val email: String?,
    val phone: String?,
    val linkedin: String?,
    val portfolio: String?,
    val workHistory: List<WorkExperience>,
) {
    companion object {
        fun of(profile: Profile): ProfileResponse =
            ProfileResponse(
                id = profile.id!!,
                name = profile.name,
                skills = profile.skills.toList(),
                experience = profile.experience,
                education = profile.education,
                targetRole = profile.targetRole,
                yearsOfExperience = profile.yearsOfExperience,
                location = profile.location,
                workAuthorization = profile.workAuthorization,
                projects = profile.projects,
                certifications = profile.certifications,
                languages = profile.languages,
                email = profile.contact.email,
                phone = profile.contact.phone,
                linkedin = profile.contact.linkedin,
                portfolio = profile.contact.portfolio,
                workHistory = profile.workHistory,
            )
    }
}
