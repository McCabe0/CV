package com.skill2career.adapter.web.job

import com.skill2career.adapter.web.security.ownerId
import com.skill2career.application.job.MatchJobs
import com.skill2career.domain.job.JobFilter
import com.skill2career.domain.job.JobMatch
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Size
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/** The live jobs that fit the signed in candidate's profile, from our copy of the boards. */
@Tag(name = "jobs")
@RestController
@RequestMapping("/jobs")
@PreAuthorize("hasRole('CANDIDATE')")
class JobMatchResource(
    private val matchJobs: MatchJobs,
) {
    @Operation(
        summary = "Jobs that fit your skills",
        description = "Best first, at most 50. 404 until you have saved a profile.",
    )
    @GetMapping("/matches")
    fun matches(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "Only jobs whose board says they are remote.")
        @RequestParam(defaultValue = "false") remoteOnly: Boolean,
        @Parameter(description = "Only jobs whose location names this, eg London or UK.")
        @RequestParam(required = false)
        @Size(max = 100) location: String?,
    ): List<JobMatchResponse> = matchJobs.match(jwt.ownerId(), JobFilter(remoteOnly, location)).map(JobMatchResponse::of)
}

data class JobMatchResponse(
    val id: Long,
    val title: String,
    val company: String,
    val location: String?,
    /** REMOTE, HYBRID or ONSITE; null when the board does not say. */
    val workplace: String?,
    /** The company's own page for the job. */
    val applyUrl: String,
    /** The candidate's skills the job asks for. */
    val matchedSkills: List<String>,
    val firstSeenAt: Instant,
) {
    companion object {
        fun of(match: JobMatch): JobMatchResponse {
            val posting = match.job.posting
            return JobMatchResponse(
                id = match.job.id!!,
                title = posting.title,
                company = match.company,
                location = posting.location,
                workplace = posting.workplace?.name,
                applyUrl = posting.applyUrl,
                matchedSkills = match.matchedSkills,
                firstSeenAt = match.job.firstSeenAt,
            )
        }
    }
}
