package com.skill2career.adapter.web.admin

import com.skill2career.adapter.jobboard.UnknownJobBoard
import com.skill2career.application.job.CompanyHealth
import com.skill2career.application.job.CrawlNow
import com.skill2career.application.job.ListCompanies
import com.skill2career.application.job.StopTracking
import com.skill2career.application.job.TrackCompany
import com.skill2career.domain.job.CrawlResult
import com.skill2career.domain.job.SourceType
import com.skill2career.domain.job.TrackedCompany
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.ErrorResponseException
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.Instant

/**
 * The tracked companies and their crawls, for admins only. Adding a company saves it and queues its crawl,
 * and stopping one closes its jobs and saves it, each in one transaction. See decisions/jobs.md.
 */
@Tag(name = "admin")
@RestController
@RequestMapping("/admin/companies")
@PreAuthorize("hasRole('ADMIN')")
class CompanyAdminResource(
    private val list: ListCompanies,
    private val track: TrackCompany,
    private val stop: StopTracking,
    private val crawlNow: CrawlNow,
) {
    @Operation(summary = "Every company, with its live jobs and last crawl", description = "Tracked companies first.")
    @GetMapping
    fun list(): List<CompanyResponse> = list.list().map(CompanyResponse::of)

    @Operation(
        summary = "Track a company's job board",
        description =
            "The board is read once first: 422 when it does not exist, 502 when it could not be read just " +
                "now. Its first crawl is queued at once. A board tracked before is tracked again.",
    )
    @PostMapping
    @Transactional
    fun track(
        @Valid @RequestBody body: TrackCompanyRequest,
    ): ResponseEntity<CompanyResponse> {
        val name = body.name.trim()
        val token = body.boardToken.trim()
        val company =
            try {
                track.track(name, body.source, token)
            } catch (unknown: UnknownJobBoard) {
                throw noSuchBoard(body.source, token, unknown)
            }
        return ResponseEntity
            .created(
                URI.create("/admin/companies/${company.id}"),
            ).body(CompanyResponse.of(CompanyHealth(company, 0, null)))
    }

    @Operation(summary = "Stop tracking a company", description = "It is kept, but no longer crawled, and its live jobs close.")
    @DeleteMapping("/{id}")
    @Transactional
    fun stop(
        @PathVariable id: Long,
    ): CompanyResponse = CompanyResponse.of(CompanyHealth(stop.stop(id), 0, null))

    @Operation(summary = "Crawl a company now", description = "409 when it is no longer tracked.")
    @PostMapping("/{id}/crawl")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun crawl(
        @PathVariable id: Long,
    ) = crawlNow.crawl(id)

    private fun noSuchBoard(
        source: SourceType,
        token: String,
        cause: UnknownJobBoard,
    ) = ErrorResponseException(
        HttpStatus.UNPROCESSABLE_CONTENT,
        ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "There is no $source board called '$token'. Check the board token.",
        ),
        cause,
    )
}

@Schema(description = "A company's job board to track.")
data class TrackCompanyRequest(
    @field:NotBlank @field:Size(max = 100)
    @field:Schema(example = "Stripe")
    val name: String,
    val source: SourceType,
    @field:NotBlank @field:Size(max = 100)
    @field:Pattern(regexp = "^\\s*[A-Za-z0-9][A-Za-z0-9._-]*\\s*$")
    @field:Schema(description = "The company's name on its board, as in its board's url.", example = "stripe")
    val boardToken: String,
)

data class CompanyResponse(
    val id: Long,
    val name: String,
    val source: SourceType,
    val boardToken: String,
    val tracked: Boolean,
    /** When an admin stopped tracking it; absent while tracked. */
    val untrackedAt: Instant?,
    val liveJobs: Int,
    /** Its latest crawl; absent before its first. */
    val lastCrawl: CrawlResponse?,
) {
    companion object {
        fun of(health: CompanyHealth): CompanyResponse {
            val company: TrackedCompany = health.company
            return CompanyResponse(
                id = company.id!!,
                name = company.name,
                source = company.source,
                boardToken = company.boardToken,
                tracked = company.tracked,
                untrackedAt = company.untrackedAt,
                liveJobs = health.liveJobs,
                lastCrawl = health.lastCrawl?.let(CrawlResponse::of),
            )
        }
    }
}

data class CrawlResponse(
    val at: Instant,
    /** WORKED; FAILED, and to be tried again; or PARKED until the next day's crawl. */
    val outcome: String,
    val found: Int,
    val added: Int,
    val closed: Int,
    /** Why it failed; absent when it worked. */
    val error: String?,
) {
    companion object {
        fun of(result: CrawlResult) = CrawlResponse(result.at, result.outcome.name, result.found, result.added, result.closed, result.error)
    }
}
