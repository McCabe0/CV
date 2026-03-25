package com.skill2career.model

data class JobMatchRequest(
    val profileId: Long? = null,
    val cvId: Long? = null,
    val generatedCvOrProfile: String,
    val profileSkills: List<String> = emptyList(),
    val jobs: List<JobItem> = emptyList()
)
