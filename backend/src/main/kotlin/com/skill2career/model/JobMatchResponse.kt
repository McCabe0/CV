package com.skill2career.model

data class JobMatchResponse(
    val profileId: Long? = null,
    val cvId: Long? = null,
    val matchIds: List<Long> = emptyList(),
    val matches: List<JobMatchResult>
)
