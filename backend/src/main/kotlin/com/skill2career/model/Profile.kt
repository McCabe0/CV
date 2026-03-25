package com.skill2career.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty

data class Profile(
    @field:NotBlank(message = "name is required")
    val name: String,

    @field:NotEmpty(message = "skills must contain at least one skill")
    val skills: List<String>,

    @field:NotBlank(message = "experience is required")
    val experience: String,

    @field:NotBlank(message = "education is required")
    val education: String,

    val targetRole: String? = null,
    val yearsOfExperience: String? = null,
    val location: String? = null,
    val workAuthorization: String? = null,
    val projects: List<String> = emptyList(),
    val certifications: List<String> = emptyList(),
    val languages: List<String> = emptyList()
)
