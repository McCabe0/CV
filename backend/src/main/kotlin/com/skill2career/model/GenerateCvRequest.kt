package com.skill2career.model

import jakarta.validation.constraints.Positive

data class GenerateCvRequest(
    @field:Positive(message = "profileId must be a positive number")
    val profileId: Long
)
