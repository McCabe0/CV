package com.skill2career.model

import jakarta.validation.constraints.Size

data class JobSearchRequest(
    @field:Size(max = 20, message = "skills can contain at most 20 items")
    val skills: List<String> = emptyList(),
    val location: String? = null,
    @field:Size(max = 20, message = "roleKeywords can contain at most 20 items")
    val roleKeywords: List<String> = emptyList()
)
