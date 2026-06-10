package com.skill2career.model

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
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
    val jobs: List<JobItem> = emptyList(),
    val includeReasoning: Boolean = false,
    @field:Min(value = 1, message = "reasoningLimit must be >= 1")
    @field:Max(value = 10, message = "reasoningLimit must be <= 10")
    val reasoningLimit: Int = 3,
    /** Preferred job location used to boost remote / location-matching roles. */
    val preferredLocation: String? = null,
    /** Candidate years of experience used for seniority alignment. */
    val candidateYears: Int? = null,
    /** Matches scoring below this value are dropped (0 = keep all). */
    @field:Min(value = 0, message = "minScore must be >= 0")
    @field:Max(value = 100, message = "minScore must be <= 100")
    val minScore: Int = 0
)
