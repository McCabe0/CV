package com.skill2career.adapter.web.admin

import com.skill2career.adapter.jobboard.JobBoardUnavailable
import com.skill2career.adapter.jobboard.UnknownJobBoard
import com.skill2career.adapter.web.ApiExceptionHandler
import com.skill2career.adapter.web.security.SecurityConfiguration
import com.skill2career.application.job.CompanyAlreadyTracked
import com.skill2career.application.job.CompanyHealth
import com.skill2career.application.job.CompanyNotTracked
import com.skill2career.application.job.CrawlNow
import com.skill2career.application.job.ListCompanies
import com.skill2career.application.job.StopTracking
import com.skill2career.application.job.TrackCompany
import com.skill2career.application.job.TrackedCompanyNotFound
import com.skill2career.domain.job.CrawlOutcome
import com.skill2career.domain.job.CrawlResult
import com.skill2career.domain.job.SourceType
import com.skill2career.domain.job.TrackedCompany
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import java.time.Instant
import kotlin.test.Test

@WebMvcTest(CompanyAdminResource::class)
@Import(SecurityConfiguration::class, ApiExceptionHandler::class)
class CompanyAdminResourceTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var list: ListCompanies

    @MockitoBean
    lateinit var track: TrackCompany

    @MockitoBean
    lateinit var stop: StopTracking

    @MockitoBean
    lateinit var crawlNow: CrawlNow

    @MockitoBean
    lateinit var jwtDecoder: JwtDecoder

    /** Adding and stopping are transactional; this slice has no database, so their transactions are stood in for. */
    @MockitoBean
    lateinit var transactions: PlatformTransactionManager

    private val at = Instant.parse("2026-11-24T06:00:00Z")
    private val monzo = TrackedCompany(1, "Monzo", SourceType.GREENHOUSE, "monzo")

    private fun admin() = jwt().jwt { it.subject("admin-sub") }.authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

    private fun candidate() = jwt().jwt { it.subject("candidate-sub") }.authorities(SimpleGrantedAuthority("ROLE_CANDIDATE"))

    private fun adding(body: String) = post("/admin/companies").with(admin()).contentType(MediaType.APPLICATION_JSON).content(body)

    private val stripe = """{"name": "Stripe", "source": "GREENHOUSE", "boardToken": "stripe"}"""

    @Test
    fun `lists every company with its live jobs and its last crawl`() {
        val parked = CrawlResult.parked(2, at, "503 from the board")
        whenever(list.list()).thenReturn(
            listOf(
                CompanyHealth(monzo, liveJobs = 53, lastCrawl = CrawlResult(1, at, CrawlOutcome.WORKED, 53, 4, 2)),
                CompanyHealth(TrackedCompany(2, "GitLab", SourceType.GREENHOUSE, "gitlab"), 246, parked),
                CompanyHealth(TrackedCompany(3, "Ramp", SourceType.ASHBY, "ramp", untrackedAt = at), 0, null),
            ),
        )

        mockMvc
            .perform(get("/admin/companies").with(admin()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].name").value("Monzo"))
            .andExpect(jsonPath("$[0].source").value("GREENHOUSE"))
            .andExpect(jsonPath("$[0].boardToken").value("monzo"))
            .andExpect(jsonPath("$[0].tracked").value(true))
            .andExpect(jsonPath("$[0].liveJobs").value(53))
            .andExpect(jsonPath("$[0].lastCrawl.at").value("2026-11-24T06:00:00Z"))
            .andExpect(jsonPath("$[0].lastCrawl.outcome").value("WORKED"))
            .andExpect(jsonPath("$[0].lastCrawl.found").value(53))
            .andExpect(jsonPath("$[0].lastCrawl.added").value(4))
            .andExpect(jsonPath("$[0].lastCrawl.closed").value(2))
            .andExpect(jsonPath("$[1].lastCrawl.outcome").value("PARKED"))
            .andExpect(jsonPath("$[1].lastCrawl.error").value("503 from the board"))
            .andExpect(jsonPath("$[2].tracked").value(false))
            .andExpect(jsonPath("$[2].untrackedAt").value("2026-11-24T06:00:00Z"))
            .andExpect(jsonPath("$[2].lastCrawl").doesNotExist())
    }

    @Test
    fun `only an admin can see or change the companies`() {
        mockMvc.perform(get("/admin/companies").with(candidate())).andExpect(status().isForbidden)
        mockMvc
            .perform(post("/admin/companies").with(candidate()).contentType(MediaType.APPLICATION_JSON).content(stripe))
            .andExpect(status().isForbidden)
        mockMvc.perform(delete("/admin/companies/1").with(candidate())).andExpect(status().isForbidden)
        mockMvc.perform(post("/admin/companies/1/crawl").with(candidate())).andExpect(status().isForbidden)
        mockMvc.perform(get("/admin/companies")).andExpect(status().isUnauthorized)

        verifyNoInteractions(list, track, stop, crawlNow)
    }

    @Test
    fun `adds a company, its first crawl queued`() {
        whenever(track.track("Stripe", SourceType.GREENHOUSE, "stripe"))
            .thenReturn(TrackedCompany(9, "Stripe", SourceType.GREENHOUSE, "stripe"))

        mockMvc
            .perform(adding(stripe))
            .andExpect(status().isCreated)
            .andExpect(header().string("Location", "/admin/companies/9"))
            .andExpect(jsonPath("$.id").value(9))
            .andExpect(jsonPath("$.tracked").value(true))
            .andExpect(jsonPath("$.liveJobs").value(0))
    }

    @Test
    fun `adds a company with its name and token trimmed`() {
        whenever(track.track("Stripe", SourceType.GREENHOUSE, "stripe"))
            .thenReturn(TrackedCompany(9, "Stripe", SourceType.GREENHOUSE, "stripe"))

        mockMvc
            .perform(adding("""{"name": "  Stripe ", "source": "GREENHOUSE", "boardToken": " stripe "}"""))
            .andExpect(status().isCreated)
    }

    @Test
    fun `refuses a company without a name, a known source, or a plain board token`() {
        mockMvc.perform(adding("""{"name": " ", "source": "GREENHOUSE", "boardToken": "stripe"}""")).andExpect(status().isBadRequest)
        mockMvc.perform(adding("""{"name": "Stripe", "source": "INDEED", "boardToken": "stripe"}""")).andExpect(status().isBadRequest)
        mockMvc.perform(adding("""{"name": "Stripe", "source": "GREENHOUSE", "boardToken": "../admin"}""")).andExpect(status().isBadRequest)
        mockMvc
            .perform(
                adding("""{"name": "${"a".repeat(101)}", "source": "GREENHOUSE", "boardToken": "x"}"""),
            ).andExpect(status().isBadRequest)

        verifyNoInteractions(track)
    }

    @Test
    fun `says plainly when the board does not exist, so the admin can fix the token`() {
        whenever(track.track("Stripe", SourceType.GREENHOUSE, "stripe")) doThrow UnknownJobBoard("there is no board 'stripe'")

        mockMvc
            .perform(adding(stripe))
            .andExpect(status().isUnprocessableContent)
            .andExpect(jsonPath("$.detail").value("There is no GREENHOUSE board called 'stripe'. Check the board token."))
    }

    @Test
    fun `says to try again when the board could not be read just now`() {
        whenever(track.track("Stripe", SourceType.GREENHOUSE, "stripe")) doThrow JobBoardUnavailable("503")

        mockMvc
            .perform(adding(stripe))
            .andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.detail").value("The board could not be read just now. Try again in a minute."))
    }

    @Test
    fun `refuses a board already tracked`() {
        whenever(track.track("Stripe", SourceType.GREENHOUSE, "stripe")) doThrow CompanyAlreadyTracked(SourceType.GREENHOUSE, "stripe")

        mockMvc.perform(adding(stripe)).andExpect(status().isConflict)
    }

    @Test
    fun `stops tracking a company, giving it back untracked`() {
        whenever(stop.stop(1)).thenReturn(monzo.untracked(at))

        mockMvc
            .perform(delete("/admin/companies/1").with(admin()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.tracked").value(false))
            .andExpect(jsonPath("$.untrackedAt").value("2026-11-24T06:00:00Z"))
    }

    @Test
    fun `crawls a company now`() {
        mockMvc.perform(post("/admin/companies/1/crawl").with(admin())).andExpect(status().isAccepted)

        verify(crawlNow).crawl(1)
    }

    @Test
    fun `will not crawl a company no longer tracked`() {
        whenever(crawlNow.crawl(1)) doThrow CompanyNotTracked(1)

        mockMvc.perform(post("/admin/companies/1/crawl").with(admin())).andExpect(status().isConflict)
    }

    @Test
    fun `a company that does not exist is not found`() {
        whenever(stop.stop(99)) doThrow TrackedCompanyNotFound(99)
        whenever(crawlNow.crawl(99)) doThrow TrackedCompanyNotFound(99)

        mockMvc.perform(delete("/admin/companies/99").with(admin())).andExpect(status().isNotFound)
        mockMvc.perform(post("/admin/companies/99/crawl").with(admin())).andExpect(status().isNotFound)
    }
}
