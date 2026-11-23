package com.skill2career.adapter.web.job

import com.skill2career.adapter.web.ApiExceptionHandler
import com.skill2career.adapter.web.security.SecurityConfiguration
import com.skill2career.application.job.FindFitSummary
import com.skill2career.application.job.FitSummaryNotFound
import com.skill2career.application.job.JobNotFound
import com.skill2career.application.job.RequestFitSummary
import com.skill2career.application.profile.ProfileNotFound
import com.skill2career.domain.job.FitSummary
import com.skill2career.domain.profile.OwnerId
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test

@WebMvcTest(JobFitResource::class)
@Import(SecurityConfiguration::class, ApiExceptionHandler::class)
class JobFitResourceTest {
    companion object {
        const val SUB = "candidate-sub"
        val OWNER = OwnerId(SUB)
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var requestFit: RequestFitSummary

    @MockitoBean
    lateinit var findFit: FindFitSummary

    @MockitoBean
    lateinit var jwtDecoder: JwtDecoder

    private fun candidate() = jwt().jwt { it.subject(SUB) }.authorities(SimpleGrantedAuthority("ROLE_CANDIDATE"))

    private val writing = FitSummary(id = 9, ownerId = OWNER, jobId = 42)

    @Test
    fun `asking for a summary accepts it, to be written, while the candidate waits`() {
        whenever(requestFit.request(OWNER, 42)).thenReturn(writing)

        mockMvc
            .perform(post("/jobs/42/fit").with(candidate()))
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.jobId").value(42))
            .andExpect(jsonPath("$.status").value("WRITING"))
            .andExpect(jsonPath("$.text").doesNotExist())
    }

    @Test
    fun `asking for a summary already written gives it at once`() {
        whenever(requestFit.request(OWNER, 42)).thenReturn(writing.written("Your Kotlin fits."))

        mockMvc
            .perform(post("/jobs/42/fit").with(candidate()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("READY"))
            .andExpect(jsonPath("$.text").value("Your Kotlin fits."))
    }

    @Test
    fun `reading a summary gives it as it is now, failed with why`() {
        whenever(findFit.find(OWNER, 42)).thenReturn(writing.failed("The AI was too busy."))

        mockMvc
            .perform(get("/jobs/42/fit").with(candidate()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.failure").value("The AI was too busy."))
    }

    @Test
    fun `a summary never asked for is not found`() {
        whenever(findFit.find(OWNER, 42)).thenThrow(FitSummaryNotFound("of job 42"))

        mockMvc.perform(get("/jobs/42/fit").with(candidate())).andExpect(status().isNotFound)
    }

    @Test
    fun `a job we do not have, or no longer listed, is not found`() {
        whenever(requestFit.request(OWNER, 42)).thenThrow(JobNotFound(42))

        mockMvc
            .perform(post("/jobs/42/fit").with(candidate()))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.detail").value("No such job, or it is no longer listed."))
    }

    @Test
    fun `a candidate with no profile cannot ask`() {
        whenever(requestFit.request(OWNER, 42)).thenThrow(ProfileNotFound(OWNER))

        mockMvc.perform(post("/jobs/42/fit").with(candidate())).andExpect(status().isNotFound)
    }

    @Test
    fun `refuses a user who is not a candidate, and a request with no token`() {
        mockMvc.perform(post("/jobs/42/fit").with(jwt().jwt { it.subject(SUB) })).andExpect(status().isForbidden)
        mockMvc.perform(get("/jobs/42/fit")).andExpect(status().isUnauthorized)

        verifyNoInteractions(requestFit, findFit)
    }
}
