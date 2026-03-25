package com.skill2career.model

data class CvResponse(
    val profileId: Long,
    val cvId: Long,
    val summary: String,
    val skills: List<String>,
    val experience: String
)
