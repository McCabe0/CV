package com.skill2career.model

data class WorkExperience(
    val company: String = "",
    val title: String = "",
    val startDate: String? = null,
    val endDate: String? = null,
    val bullets: List<String> = emptyList()
)
