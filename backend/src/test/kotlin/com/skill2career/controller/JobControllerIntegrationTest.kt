package com.skill2career.controller

import com.skill2career.entity.UserProfileEntity
import com.skill2career.model.JobItem
import com.skill2career.service.GeminiService
import com.skill2career.service.PersistenceService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class JobControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var geminiService: GeminiService
    @MockBean
    private lateinit var persistenceService: PersistenceService

    private val aiJobs = listOf(
        JobItem(
            id = "ai-1",
            title = "Backend Kotlin Engineer",
            company = "AI Corp",
            location = "Remote",
            description = "Build APIs",
            requiredSkills = listOf("Kotlin", "Spring Boot", "REST", "SQL"),
            roleKeywords = listOf("backend")
        ),
        JobItem(
            id = "ai-2",
            title = "Data Engineer",
            company = "AI Data",
            location = "Austin, TX",
            description = "Data pipelines",
            requiredSkills = listOf("SQL", "Python", "ETL"),
            roleKeywords = listOf("data")
        ),
        JobItem(
            id = "ai-3",
            title = "Frontend Engineer",
            company = "AI UI",
            location = "Remote",
            description = "Frontend",
            requiredSkills = listOf("React", "TypeScript"),
            roleKeywords = listOf("frontend")
        )
    )

    @Test
    fun `POST jobs search returns ai jobs`() {
        whenever(geminiService.generateJobsForSearch(any())).thenReturn(aiJobs)
        whenever(persistenceService.saveSearchedJobs(any())).thenReturn(emptyList())

        val requestBody = """
            {
              "skills": ["Kotlin"],
              "location": "Remote",
              "roleKeywords": ["backend"]
            }
        """.trimIndent()

        mockMvc.perform(
            post("/jobs/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.searchId").value(-1))
            .andExpect(jsonPath("$.jobs.length()").value(3))
            .andExpect(jsonPath("$.jobs[0].id").value("ai-1"))
    }

    @Test
    fun `POST jobs match returns scored matches`() {
        whenever(geminiService.generateMatchReasoning(any(), any(), any(), any())).thenReturn("Model reasoning")
        whenever(persistenceService.saveMatchResults(any(), any(), any())).thenReturn(emptyList())

        val requestBody = """
            {
              "generatedCvOrProfile": "Kotlin Spring Boot REST SQL",
              "profileSkills": ["Kotlin", "Spring Boot", "REST", "SQL"],
              "jobs": [
                {
                  "id": "job-it-1",
                  "title": "Backend Kotlin Engineer",
                  "company": "Skill2Career",
                  "location": "Remote",
                  "description": "Build APIs",
                  "requiredSkills": ["Kotlin", "Spring Boot", "REST", "SQL"],
                  "roleKeywords": ["backend"]
                }
              ]
            }
        """.trimIndent()

        mockMvc.perform(
            post("/jobs/match")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.matches.length()").value(1))
            .andExpect(jsonPath("$.matches[0].score").value(90))
            .andExpect(jsonPath("$.matches[0].skillOverlapPercent").value(100))
            .andExpect(jsonPath("$.matches[0].confidence").value(100))
            .andExpect(jsonPath("$.matches[0].reasoning").value("Model reasoning"))
    }

    @Test
    fun `GET jobs recommendations returns top ranked ai jobs`() {
        whenever(geminiService.generateJobsForSearch(any())).thenReturn(aiJobs)
        whenever(geminiService.generateMatchReasoning(any(), any(), any(), any())).thenReturn("Model reasoning")
        whenever(persistenceService.saveSearchedJobs(any())).thenReturn(emptyList())
        whenever(persistenceService.saveMatchResults(any(), any(), any())).thenReturn(emptyList())
        whenever(persistenceService.getProfile(any())).thenReturn(UserProfileEntity(id = 1L, skills = "Kotlin||Spring Boot||REST"))

        mockMvc.perform(get("/jobs/recommendations/1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.matches.length()").value(3))
            .andExpect(jsonPath("$.matches[0].score").isNumber)
            .andExpect(jsonPath("$.matches[0].job.id").isNotEmpty)
    }

    @Test
    fun `GET jobs recommendations returns 404 when profile is missing`() {
        whenever(persistenceService.getProfile(999L)).thenReturn(null)

        mockMvc.perform(get("/jobs/recommendations/999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("Resource not found"))
            .andExpect(jsonPath("$.message").value("Profile not found: 999"))
    }
}
