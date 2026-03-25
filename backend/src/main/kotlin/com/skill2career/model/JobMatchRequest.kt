package com.skill2career.model

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class JobMatchRequest(
    val profileId: Long? = null,
    val cvId: Long? = null,
    @field:NotBlank(message = "generatedCvOrProfile is required")
    val generatedCvOrProfile: String,
    @field:Size(max = 50, message = "profileSkills can contain at most 50 items")
    val profileSkills: List<String> = emptyList(),
    @field:Size(min = 1, message = "jobs must contain at least one item")
    val jobs: List<JobItem> = emptyList()
) {
    @get:AssertTrue(message = "Either profileId or cvId must be provided")
    val hasProfileOrCvId: Boolean
        get() = profileId != null || cvId != null
}
