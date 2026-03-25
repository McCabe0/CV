package com.skill2career.model

data class JobSearchResponse(
    val searchId: Long,
    val savedJobIds: List<Long>,
    val jobs: List<JobItem>
)
