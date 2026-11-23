package com.skill2career.adapter.web.job

import com.skill2career.adapter.web.ApiExceptionHandler
import com.skill2career.adapter.web.security.SecurityConfiguration
import com.skill2career.application.job.MatchJobs
import com.skill2career.application.profile.ProfileNotFound
import com.skill2career.domain.job.JobFilter
import com.skill2career.domain.job.JobFixtures.FIRST_SEEN
import com.skill2career.domain.job.JobFixtures.job
import com.skill2career.domain.job.JobFixtures.posting
import com.skill2career.domain.job.JobMatch
import com.skill2career.domain.job.Workplace
import com.skill2career.domain.profile.OwnerId
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test

@WebMvcTest(JobMatchResource::class)
@Import(SecurityConfiguration::class, ApiExceptionHandler::class)
class JobMatchResourceTest {
    companion object {
        const val SUB = "candidate-sub"
        val OWNER = OwnerId(SUB)
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var matchJobs: MatchJobs

    @MockitoBean
    lateinit var jwtDecoder: JwtDecoder

    private fun candidate() = jwt().jwt { it.subject(SUB) }.authorities(SimpleGrantedAuthority("ROLE_CANDIDATE"))

    private val match =
        JobMatch(
            job =
                job(
                    id = 42,
                    posting =
                        posting(
                            title = "Backend Engineer",
                            location = "London or Remote (UK)",
                            applyUrl = "https://monzo.com/careers/4001",
                            workplace = Workplace.REMOTE,
                        ),
                ),
            company = "Monzo",
            matchedSkills = listOf("Kotlin", "Kafka"),
        )

    @Test
    fun `returns the signed in candidate's matches, each linking to the company's own page`() {
        whenever(matchJobs.match(OWNER, JobFilter())).thenReturn(listOf(match))

        mockMvc
            .perform(get("/jobs/matches").with(candidate()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(42))
            .andExpect(jsonPath("$[0].title").value("Backend Engineer"))
            .andExpect(jsonPath("$[0].company").value("Monzo"))
            .andExpect(jsonPath("$[0].location").value("London or Remote (UK)"))
            .andExpect(jsonPath("$[0].workplace").value("REMOTE"))
            .andExpect(jsonPath("$[0].applyUrl").value("https://monzo.com/careers/4001"))
            .andExpect(jsonPath("$[0].matchedSkills[1]").value("Kafka"))
            .andExpect(jsonPath("$[0].firstSeenAt").value(FIRST_SEEN.toString()))
            .andExpect(jsonPath("$[0].description").doesNotExist())
    }

    @Test
    fun `narrows to remote jobs in a place`() {
        whenever(matchJobs.match(any(), any())).thenReturn(emptyList())

        mockMvc
            .perform(get("/jobs/matches").param("remoteOnly", "true").param("location", "UK").with(candidate()))
            .andExpect(status().isOk)

        verify(matchJobs).match(OWNER, JobFilter(remoteOnly = true, location = "UK"))
    }

    @Test
    fun `gives a job whose board does not say where it is done no workplace`() {
        whenever(matchJobs.match(any(), any())).thenReturn(listOf(match.copy(job = job(id = 7, posting = posting(workplace = null)))))

        mockMvc
            .perform(get("/jobs/matches").with(candidate()))
            .andExpect(jsonPath("$[0].workplace").doesNotExist())
    }

    @Test
    fun `refuses a location longer than any place`() {
        mockMvc
            .perform(get("/jobs/matches").param("location", "x".repeat(101)).with(candidate()))
            .andExpect(status().isBadRequest)

        verifyNoInteractions(matchJobs)
    }

    @Test
    fun `says not found when the candidate has no profile to match`() {
        whenever(matchJobs.match(any(), any())).thenThrow(ProfileNotFound(OWNER))

        mockMvc
            .perform(get("/jobs/matches").with(candidate()))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.detail").value("You have no profile yet."))
    }

    @Test
    fun `refuses a user who is not a candidate`() {
        mockMvc
            .perform(get("/jobs/matches").with(jwt().jwt { it.subject(SUB) }))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `refuses a request with no token`() {
        mockMvc.perform(get("/jobs/matches")).andExpect(status().isUnauthorized)
    }
}
