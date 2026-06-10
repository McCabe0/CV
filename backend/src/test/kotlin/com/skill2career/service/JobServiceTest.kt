package com.skill2career.service

import com.skill2career.entity.JobEntity
import com.skill2career.entity.JobMatchEntity
import com.skill2career.entity.UserProfileEntity
import com.skill2career.model.JobItem
import com.skill2career.model.JobMatchRequest
import com.skill2career.model.JobSearchRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
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
        whenever(persistenceService.getProfile(any())).thenReturn(
            UserProfileEntity(id = 1L, skills = "Kotlin||Spring Boot||REST")
        )

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
            jobs = listOf(aiJobs.first()),
            includeReasoning = true
        )

        val response = jobService.matchJobs(request)

        assertEquals(1, response.matches.size)
        val first = response.matches.first()
        assertEquals(100, first.skillOverlapPercent)
        // overlap 100 * 0.7 + title-alignment bonus 8 (no missing skills) = 78
        assertEquals(78, first.score)
        // 55 + overlap 100 * 0.35 = 90
        assertEquals(90, first.confidence)
        assertTrue(first.requiredSkillsMissing.isEmpty())
        assertEquals("Deterministic test reasoning", first.reasoning)
        assertEquals(listOf(10L), response.matchIds)
    }

    @Test
    fun `matchJobs only generates reasoning up to the reasoning limit`() {
        val request = JobMatchRequest(
            generatedCvOrProfile = "Experienced in Kotlin Spring Boot REST SQL APIs",
            profileSkills = listOf("Kotlin", "Spring Boot", "REST", "SQL"),
            jobs = listOf(aiJobs[0], aiJobs[1]),
            includeReasoning = true,
            reasoningLimit = 1
        )

        val response = jobService.matchJobs(request)

        assertEquals(2, response.matches.size)
        // Top-ranked match (within the limit) gets generated reasoning.
        assertEquals("Deterministic test reasoning", response.matches.first().reasoning)
        // Matches beyond the reasoning limit keep the default placeholder.
        assertEquals("Reasoning not requested", response.matches[1].reasoning)
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
        // 55 - missingPenalty(3*6=18) * 0.25 = 50.5 -> 51
        assertEquals(51, first.confidence)
        assertEquals(listOf("SQL", "Python", "ETL"), first.requiredSkillsMissing)
    }

    @Test
    fun `recommendations returns ranked jobs using profile skills`() {
        whenever(persistenceService.getProfile(1L)).thenReturn(
            UserProfileEntity(id = 1L, skills = "Kotlin||Spring Boot||REST")
        )

        val response = jobService.recommendations(1L)

        // All four AI jobs are returned (take(6)), ranked by descending score.
        assertEquals(4, response.matches.size)
        assertTrue(response.matches.zipWithNext().all { it.first.score >= it.second.score })
        assertFalse(response.matches.first().job.id.isBlank())

        // Recommendations run with includeReasoning = false, so reasoning is not generated.
        verify(geminiService, atLeastOnce()).generateJobsForSearch(any())
    }

    @Test
    fun `recommendations throws when profile does not exist`() {
        whenever(persistenceService.getProfile(404L)).thenReturn(null)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            jobService.recommendations(404L)
        }

        assertEquals("Profile not found: 404", ex.message)
    }

    @Test
    fun `recommendations throws when profile skills are empty`() {
        whenever(persistenceService.getProfile(12L)).thenReturn(
            UserProfileEntity(id = 12L, skills = " || ")
        )

        val ex = assertThrows(IllegalArgumentException::class.java) {
            jobService.recommendations(12L)
        }

        assertEquals("Profile has no skills: 12", ex.message)
    }

    @Test
    fun `matchJobs rewards remote and location-aligned roles`() {
        // Remote roles always earn the location bonus when a preference is supplied.
        val remote = jobService.matchJobs(
            requestFor(fullyMatchingJob(location = "Remote"), preferredLocation = "Austin, TX")
        )
        assertEquals(83, remote.matches.first().score)

        // A job whose location matches the preference earns the bonus.
        val aligned = jobService.matchJobs(
            requestFor(fullyMatchingJob(location = "Austin, TX"), preferredLocation = "Austin, TX")
        )
        assertEquals(83, aligned.matches.first().score)

        // A mismatched location earns no bonus.
        val mismatched = jobService.matchJobs(
            requestFor(fullyMatchingJob(location = "Berlin"), preferredLocation = "London")
        )
        assertEquals(78, mismatched.matches.first().score)
    }

    @Test
    fun `matchJobs adjusts score and confidence by candidate seniority`() {
        // Senior candidate (10y) meeting a mid-level role: +5 score, +confidence.
        val qualified = jobService.matchJobs(
            requestFor(fullyMatchingJob(title = "Backend Kotlin Engineer"), candidateYears = 10)
        )
        assertEquals(83, qualified.matches.first().score)
        assertEquals(93, qualified.matches.first().confidence)

        // Junior candidate (1y) under-qualified for a senior role: -8 score, -confidence.
        val underQualified = jobService.matchJobs(
            requestFor(fullyMatchingJob(title = "Senior Backend Kotlin Engineer"), candidateYears = 1)
        )
        assertEquals(70, underQualified.matches.first().score)
        assertEquals(86, underQualified.matches.first().confidence)

        // Mid-level candidate (5y) over-qualified for a junior role: +5 score.
        val midLevel = jobService.matchJobs(
            requestFor(fullyMatchingJob(title = "Junior Backend Role"), candidateYears = 5)
        )
        assertEquals(75, midLevel.matches.first().score)
    }

    @Test
    fun `matchJobs drops matches below the minimum score`() {
        val strong = fullyMatchingJob()
        val weak = JobItem(
            id = "weak",
            title = "Data Engineer",
            company = "AI Data",
            location = "Austin, TX",
            description = "Data pipelines",
            requiredSkills = listOf("SQL", "Python", "ETL"),
            roleKeywords = listOf("data")
        )
        val request = JobMatchRequest(
            generatedCvOrProfile = "Experienced in Kotlin Spring Boot REST SQL APIs",
            profileSkills = listOf("Kotlin", "Spring Boot", "REST", "SQL"),
            jobs = listOf(strong, weak),
            minScore = 50
        )

        val response = jobService.matchJobs(request)

        assertEquals(1, response.matches.size)
        assertEquals("match-job", response.matches.first().job.id)
    }

    private fun fullyMatchingJob(
        title: String = "Backend Kotlin Engineer",
        location: String = "Remote"
    ): JobItem = JobItem(
        id = "match-job",
        title = title,
        company = "AI Corp",
        location = location,
        description = "Build backend APIs",
        requiredSkills = listOf("Kotlin", "Spring Boot", "REST", "SQL"),
        roleKeywords = listOf("backend")
    )

    private fun requestFor(
        job: JobItem,
        preferredLocation: String? = null,
        candidateYears: Int? = null,
        minScore: Int = 0
    ): JobMatchRequest = JobMatchRequest(
        generatedCvOrProfile = "Experienced in Kotlin Spring Boot REST SQL APIs",
        profileSkills = listOf("Kotlin", "Spring Boot", "REST", "SQL"),
        jobs = listOf(job),
        preferredLocation = preferredLocation,
        candidateYears = candidateYears,
        minScore = minScore
    )
}
