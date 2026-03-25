package com.skill2career.service

import com.skill2career.model.JobItem
import com.skill2career.model.JobMatchRequest
import com.skill2career.model.JobSearchRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito

class JobServiceTest {

    private lateinit var geminiService: GeminiService
    private lateinit var jobService: JobService

    @BeforeEach
    fun setUp() {
        geminiService = Mockito.mock(GeminiService::class.java)
        Mockito.`when`(
            geminiService.generateMatchReasoning(anyString(), Mockito.any(JobItem::class.java), anyInt(), anyList())
        ).thenReturn("Deterministic test reasoning")

        jobService = JobService(geminiService)
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
    fun `matchJobs computes deterministic fields`() {
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
    fun `recommendations returns top three ranked jobs`() {
        val response = jobService.recommendations("backend-profile")

        assertEquals(3, response.matches.size)
        assertTrue(response.matches.zipWithNext().all { it.first.score >= it.second.score })
        assertFalse(response.matches.first().job.id.isBlank())

        Mockito.verify(geminiService, Mockito.atLeastOnce()).generateMatchReasoning(
            anyString(),
            Mockito.any(JobItem::class.java),
            anyInt(),
            anyList()
        )
    }
}
