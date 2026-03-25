package com.skill2career.model

data class JobMatchResult(
    val job: JobItem,
    val score: Int,
    val skillOverlapPercent: Int,
    val requiredSkillsMissing: List<String>,
    val confidence: Int,
    val reasoning: String
)
