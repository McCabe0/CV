package com.skill2career.model

data class JobItem(
    val id: String,
    val title: String,
    val company: String,
    val location: String,
    val description: String,
    val requiredSkills: List<String> = emptyList(),
    val roleKeywords: List<String> = emptyList(),
    val source: String = "internal",
    val url: String? = null
)
