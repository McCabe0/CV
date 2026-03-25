package com.skill2career.model

data class JobMatchRequest(
    val generatedCvOrProfile: String,
    val profileSkills: List<String> = emptyList(),
    val jobs: List<JobItem> = emptyList()
)
