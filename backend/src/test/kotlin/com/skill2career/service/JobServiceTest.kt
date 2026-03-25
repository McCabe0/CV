package com.skill2career.service

import com.skill2career.model.JobItem
import com.skill2career.model.JobMatchRequest
import com.skill2career.model.JobSearchRequest
import com.skill2career.entity.JobEntity
import com.skill2career.entity.JobMatchEntity
import com.skill2career.entity.UserProfileEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class JobServiceTest {

    private lateinit var geminiService: GeminiService
    private lateinit var persistenceService: PersistenceService
    private lateinit var jobService: JobService

    private val aiJobs = listOf(
        JobItem(
            id = "ai-1",
            title = "Backend Kotlin Engineer",
            company = "AI Corp",
            location = "Remote",
            description = "Build backend APIs",
            requiredSkills = listOf("Kotlin", "Spring Boot", "REST", "SQL"),
            roleKeywords = listOf("backend")
        ),
        JobItem(
            id = "ai-2",
            title = "Frontend React Developer",
            company = "AI Labs",
            location = "San Francisco, CA",
            description = "Build web apps",
            requiredSkills = listOf("React", "TypeScript"),
            roleKeywords = listOf("frontend")
        ),
        JobItem(
            id = "ai-3",
            title = "Data Engineer",
            company = "AI Data",
            location = "Austin, TX",
            description = "Data pipelines",
            requiredSkills = listOf("SQL", "Python", "ETL"),
            roleKeywords = listOf("data")
        ),
        JobItem(
            id = "ai-4",
            title = "Generalist Engineer",
            company = "AI Startup",
            location = "Remote",
            description = "Various tasks",
            requiredSkills = emptyList(),
            roleKeywords = listOf("generalist")
        )
    )

    @BeforeEach
    fun setUp() {
        geminiService = mock()
        persistenceService = mock()
        whenever(geminiService.generateMatchReasoning(any(), any(), any(), any()))
            .thenReturn("Deterministic test reasoning")
        whenever(geminiService.generateJobsForSearch(any())).thenReturn(aiJobs)
        whenever(persistenceService.saveSearchedJobs(any())).thenReturn(
            listOf(
                JobEntity(id = 1L), JobEntity(id = 2L), JobEntity(id = 3L), JobEntity(id = 4L)
            )
        )
        whenever(persistenceService.saveMatchResults(anyOrNull(), anyOrNull(), any())).thenReturn(
            listOf(JobMatchEntity(id = 10L))
        )
        whenever(persistenceService.getProfile(any())).thenReturn(null)

        jobService = JobService(geminiService, persistenceService)
    }

    @Test
    fun `searchJobs returns ai jobs`() {
        val response = jobService.searchJobs(JobSearchRequest(skills = listOf("kotlin")))
        assertEquals(4, response.jobs.size)
        assertEquals(1L, response.searchId)
        assertEquals(4, response.savedJobIds.size)
        assertEquals("ai-1", response.jobs.first().id)
    }

    @Test
    fun `searchJobs returns fallback search id when nothing persisted`() {
        whenever(persistenceService.saveSearchedJobs(any())).thenReturn(emptyList())

        val response = jobService.searchJobs(JobSearchRequest(skills = listOf("kotlin")))

        assertEquals(-1L, response.searchId)
        assertTrue(response.savedJobIds.isEmpty())
    }

    @Test
    fun `matchJobs computes deterministic fields with keyword bonus`() {
        val request = JobMatchRequest(
            generatedCvOrProfile = "Experienced in Kotlin Spring Boot REST SQL APIs",
            profileSkills = listOf("Kotlin", "Spring Boot", "REST", "SQL"),
            jobs = listOf(aiJobs.first())
        )

        val response = jobService.matchJobs(request)

        assertEquals(1, response.matches.size)
        val first = response.matches.first()
        assertEquals(100, first.skillOverlapPercent)
        assertEquals(90, first.score)
        assertEquals(100, first.confidence)
        assertTrue(first.requiredSkillsMissing.isEmpty())
        assertEquals("Deterministic test reasoning", first.reasoning)
        assertEquals(listOf(10L), response.matchIds)
    }

    @Test
    fun `matchJobs computes zero overlap and missing skills without keyword bonus`() {
        val request = JobMatchRequest(
            generatedCvOrProfile = "Experienced in communication and planning",
            profileSkills = listOf("Leadership"),
            jobs = listOf(aiJobs[2])
        )

        val response = jobService.matchJobs(request)
        val first = response.matches.first()

        assertEquals(0, first.skillOverlapPercent)
        assertEquals(0, first.score)
        assertEquals(60, first.confidence)
        assertEquals(listOf("SQL", "Python", "ETL"), first.requiredSkillsMissing)
    }

    @Test
    fun `recommendations returns top three ranked jobs using ai results`() {
        val response = jobService.recommendations(1L)

        assertEquals(3, response.matches.size)
        assertTrue(response.matches.zipWithNext().all { it.first.score >= it.second.score })
        assertFalse(response.matches.first().job.id.isBlank())

        verify(geminiService, atLeastOnce()).generateJobsForSearch(any())
        verify(geminiService, atLeastOnce()).generateMatchReasoning(any(), any(), any(), any())
    }

    @Test
    fun `recommendations uses persisted profile skills when available`() {
        whenever(persistenceService.getProfile(9L)).thenReturn(
            UserProfileEntity(id = 9L, skills = "Go||Kubernetes||Docker")
        )

        val response = jobService.recommendations(9L)

        assertEquals(3, response.matches.size)
        assertEquals(9L, response.profileId)
    }

    @Test
    fun `recommendations uses alternate fallback branches`() {
        val id2 = jobService.recommendations(2L)
        val id3 = jobService.recommendations(3L)

        assertEquals(3, id2.matches.size)
        assertEquals(3, id3.matches.size)
    }
}
