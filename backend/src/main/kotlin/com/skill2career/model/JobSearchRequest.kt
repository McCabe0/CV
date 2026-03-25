package com.skill2career.model

data class JobSearchRequest(
    val skills: List<String> = emptyList(),
    val location: String? = null,
    val roleKeywords: List<String> = emptyList()
)
