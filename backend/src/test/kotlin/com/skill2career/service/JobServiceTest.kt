package com.skill2career.service

import com.skill2career.model.JobItem
import com.skill2career.model.JobMatchRequest
import com.skill2career.model.JobSearchRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class JobServiceTest {

    private lateinit var geminiService: GeminiService
    private lateinit var jobService: JobService

    @BeforeEach
    fun setUp() {
        geminiService = mock()
        whenever(
            geminiService.generateMatchReasoning(any(), any(), any(), any())
        ).thenReturn("Deterministic test reasoning")

        jobService = JobService(geminiService)
    }

    @Test
    fun `searchJobs returns all jobs when no filters are provided`() {
        val response = jobService.searchJobs(JobSearchRequest())
        assertEquals(4, response.jobs.size)
    }

    @Test
    fun `searchJobs filters by skill keyword and location`() {
        val response = jobService.searchJobs(
            JobSearchRequest(
                skills = listOf("kotlin"),
                location = "remote",
                roleKeywords = listOf("backend")
            )
        )

        assertEquals(1, response.jobs.size)
        assertEquals("Backend Kotlin Engineer", response.jobs.first().title)
    }

    @Test
    fun `searchJobs can match role keyword from title`() {
        val response = jobService.searchJobs(
            JobSearchRequest(
                roleKeywords = listOf("react")
            )
        )

        assertEquals(1, response.jobs.size)
        assertEquals("Frontend React Developer", response.jobs.first().title)
    }

    @Test
    fun `searchJobs returns empty list when location does not match`() {
        val response = jobService.searchJobs(
            JobSearchRequest(
                location = "Miami, FL",
                skills = listOf("Kotlin")
            )
        )

        assertTrue(response.jobs.isEmpty())
    }

    @Test
    fun `matchJobs computes deterministic fields with keyword bonus`() {
        val request = JobMatchRequest(
            generatedCvOrProfile = "Experienced in Kotlin Spring Boot REST SQL APIs",
            profileSkills = listOf("Kotlin", "Spring Boot", "REST", "SQL"),
            jobs = listOf(
                JobItem(
                    id = "job-test-1",
                    title = "Backend Kotlin Engineer",
                    company = "Skill2Career",
                    location = "Remote",
                    description = "Build Spring Boot APIs",
                    requiredSkills = listOf("Kotlin", "Spring Boot", "REST", "SQL"),
                    roleKeywords = listOf("backend")
                )
            )
        )

        val response = jobService.matchJobs(request)

        assertEquals(1, response.matches.size)
        val first = response.matches.first()
        assertEquals(100, first.skillOverlapPercent)
        assertEquals(90, first.score)
        assertEquals(100, first.confidence)
        assertTrue(first.requiredSkillsMissing.isEmpty())
        assertEquals("Deterministic test reasoning", first.reasoning)
    }

    @Test
    fun `matchJobs computes zero overlap and missing skills without keyword bonus`() {
        val request = JobMatchRequest(
            generatedCvOrProfile = "Experienced in leadership and communication",
            profileSkills = listOf("Leadership"),
            jobs = listOf(
                JobItem(
                    id = "job-test-2",
                    title = "Data Engineer",
                    company = "InsightGrid",
                    location = "Austin, TX",
                    description = "Build ETL systems",
                    requiredSkills = listOf("SQL", "Python"),
                    roleKeywords = listOf("data")
                )
            )
        )

        val response = jobService.matchJobs(request)
        val first = response.matches.first()

        assertEquals(0, first.skillOverlapPercent)
        assertEquals(0, first.score)
        assertEquals(60, first.confidence)
        assertEquals(listOf("SQL", "Python"), first.requiredSkillsMissing)
    }

    @Test
    fun `matchJobs handles jobs with empty required skills`() {
        val request = JobMatchRequest(
            generatedCvOrProfile = "Generalist profile",
            profileSkills = listOf("Kotlin"),
            jobs = listOf(
                JobItem(
                    id = "job-test-3",
                    title = "Generalist",
                    company = "Acme",
                    location = "Remote",
                    description = "Do many things",
                    requiredSkills = emptyList(),
                    roleKeywords = emptyList()
                )
            )
        )

        val response = jobService.matchJobs(request)
        val first = response.matches.first()

        assertEquals(0, first.skillOverlapPercent)
        assertEquals(0, first.score)
        assertEquals(60, first.confidence)
        assertTrue(first.requiredSkillsMissing.isEmpty())
    }

    @Test
    fun `recommendations return top three for data profile path`() {
        val response = jobService.recommendations("data-user")

        assertEquals(3, response.matches.size)
        assertTrue(response.matches.zipWithNext().all { it.first.score >= it.second.score })
    }

    @Test
    fun `recommendations return top three for frontend profile path`() {
        val response = jobService.recommendations("frontend-user")

        assertEquals(3, response.matches.size)
        assertTrue(response.matches.zipWithNext().all { it.first.score >= it.second.score })
    }

    @Test
    fun `recommendations returns top three ranked jobs for default path`() {
        val response = jobService.recommendations("backend-profile")

        assertEquals(3, response.matches.size)
        assertTrue(response.matches.zipWithNext().all { it.first.score >= it.second.score })
        assertFalse(response.matches.first().job.id.isBlank())

        verify(geminiService, atLeastOnce()).generateMatchReasoning(
            any(),
            any(),
            any(),
            any()
        )
    }
}
