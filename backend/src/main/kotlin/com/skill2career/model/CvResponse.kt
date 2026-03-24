package com.skill2career.model

data class CvResponse(
    val summary: String,
    val skills: List<String>,
    val experience: List<String>
)
