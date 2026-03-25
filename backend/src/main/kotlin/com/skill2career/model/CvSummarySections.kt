package com.skill2career.model

data class CvSummarySections(
    val headline: String,
    val summary: String,
    val keySkills: List<String>,
    val experienceBullets: List<String>,
    val educationSection: String,
    val atsKeywords: List<String>
)
