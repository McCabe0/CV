package com.skill2career.adapter.web.job

import com.skill2career.adapter.web.security.ownerId
import com.skill2career.application.job.FindFitSummary
import com.skill2career.application.job.RequestFitSummary
import com.skill2career.domain.job.FitStatus
import com.skill2career.domain.job.FitSummary
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Why a job fits the signed in candidate: asked for once, written by the cv-worker, then kept. */
@Tag(name = "jobs")
@RestController
@RequestMapping("/jobs/{jobId}/fit")
@PreAuthorize("hasRole('CANDIDATE')")
class JobFitResource(
    private val requestFit: RequestFitSummary,
    private val findFit: FindFitSummary,
) {
    @Operation(
        summary = "Ask why a job fits you",
        description =
            "202 while it is written: read it with GET until it is READY or FAILED. 200 when it was " +
                "already written. Asking again after it FAILED writes it afresh.",
    )
    @PostMapping
    fun request(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable jobId: Long,
    ): ResponseEntity<FitSummaryResponse> {
        val summary = requestFit.request(jwt.ownerId(), jobId)
        val response = FitSummaryResponse.of(summary)
        return if (summary.status == FitStatus.WRITING) ResponseEntity.accepted().body(response) else ResponseEntity.ok(response)
    }

    @Operation(summary = "Why a job fits you", description = "404 until you have asked.")
    @GetMapping
    fun find(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable jobId: Long,
    ): FitSummaryResponse = FitSummaryResponse.of(findFit.find(jwt.ownerId(), jobId))
}

data class FitSummaryResponse(
    val jobId: Long,
    /** WRITING, READY or FAILED. */
    val status: String,
    /** Why the job fits, once READY. */
    val text: String?,
    /** Why it could not be written, once FAILED. Asking again writes it afresh. */
    val failure: String?,
) {
    companion object {
        fun of(summary: FitSummary) = FitSummaryResponse(summary.jobId, summary.status.name, summary.text, summary.failure)
    }
}
