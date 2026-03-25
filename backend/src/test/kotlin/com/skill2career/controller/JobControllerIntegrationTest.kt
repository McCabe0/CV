package com.skill2career.controller

import com.skill2career.model.JobItem
import com.skill2career.service.GeminiService
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
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

    @Test
    fun `POST jobs search returns matching jobs`() {
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
            .andExpect(jsonPath("$.jobs.length()").value(1))
            .andExpect(jsonPath("$.jobs[0].title").value("Backend Kotlin Engineer"))
    }


    @Test
    fun `POST jobs search without filters returns full catalog`() {
        val requestBody = """
            {
              "skills": [],
              "location": null,
              "roleKeywords": []
            }
        """.trimIndent()

        mockMvc.perform(
            post("/jobs/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.jobs.length()").value(4))
    }

    @Test
    fun `POST jobs match returns scored matches`() {
        Mockito.`when`(
            geminiService.generateMatchReasoning(anyString(), Mockito.any(JobItem::class.java), anyInt(), anyList())
        ).thenReturn("Model reasoning")

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
    fun `GET jobs recommendations returns top ranked jobs`() {
        Mockito.`when`(
            geminiService.generateMatchReasoning(anyString(), Mockito.any(JobItem::class.java), anyInt(), anyList())
        ).thenReturn("Model reasoning")

        mockMvc.perform(get("/jobs/recommendations/profile-1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.matches.length()").value(3))
            .andExpect(jsonPath("$.matches[0].score").isNumber)
            .andExpect(jsonPath("$.matches[0].job.id").isNotEmpty)
            .andExpect(jsonPath("$.matches[0].score").value(org.hamcrest.Matchers.greaterThanOrEqualTo(0)))
    }
}
