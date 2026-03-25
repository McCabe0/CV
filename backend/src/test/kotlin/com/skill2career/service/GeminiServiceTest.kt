package com.skill2career.service

import com.skill2career.model.JobItem
import com.skill2career.model.JobSearchRequest
import com.skill2career.model.Profile
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class GeminiServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var geminiService: GeminiService

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()

        val webClient = WebClient.builder()
            .baseUrl(server.url("/").toString().removeSuffix("/"))
            .build()

        geminiService = GeminiService(webClient, "test-api-key")
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `generateSummary returns structured sections from Gemini response`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "candidates": [
                        {
                          "content": {
                            "parts": [
                              { "text": "{\"headline\":\"Backend Engineer\",\"summary\":\"Built APIs\",\"keySkills\":[\"Kotlin\"],\"experienceBullets\":[\"3 years backend development\"],\"educationSection\":\"BS Computer Science\",\"atsKeywords\":[\"Kotlin\",\"Spring Boot\"]}" }
                            ]
                          }
                        }
                      ]
                    }
                    """.trimIndent()
                )
        )

        val summary = geminiService.generateSummary(
            Profile(
                name = "Alex",
                skills = listOf("Kotlin"),
                experience = "3 years",
                education = "BS"
            )
        )

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/models/gemini-flash-latest:generateContent", request.path)
        assertEquals("test-api-key", request.getHeader("x-goog-api-key"))
        assertEquals("Backend Engineer", summary.headline)
        assertEquals("Built APIs", summary.summary)
        assertEquals(listOf("Kotlin"), summary.keySkills)
    }


    @Test
    fun `generateSummary parses fenced json payload`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "candidates": [
                        {
                          "content": {
                            "parts": [
                              { "text": "```json\n{\"headline\":\"Platform Engineer\",\"summary\":\"Distributed systems\",\"keySkills\":[\"Kotlin\"],\"experienceBullets\":[\"Led service migration\"],\"educationSection\":\"MS CS\",\"atsKeywords\":[\"Kotlin\"]}\n```" }
                            ]
                          }
                        }
                      ]
                    }
                    """.trimIndent()
                )
        )

        val summary = geminiService.generateSummary(
            Profile(
                name = "Jordan",
                skills = listOf("Kotlin"),
                experience = "6 years",
                education = "MS"
            )
        )

        assertEquals("Platform Engineer", summary.headline)
        assertEquals("Distributed systems", summary.summary)
    }

    @Test
    fun `generateSummary returns fallback when candidate text contains no json object`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "candidates": [
                        {
                          "content": {
                            "parts": [
                              { "text": "summary unavailable" }
                            ]
                          }
                        }
                      ]
                    }
                    """.trimIndent()
                )
        )

        val summary = geminiService.generateSummary(
            Profile(
                name = "Taylor",
                skills = listOf("Spring Boot"),
                experience = "4 years",
                education = "BS"
            )
        )

        assertEquals("Professional Profile", summary.headline)
        assertEquals("Failed to generate summary", summary.summary)
    }

    @Test
    fun `generateJobsForSearch parses job list from response`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "candidates": [
                        {
                          "content": {
                            "parts": [
                              { "text": "[{\"id\":\"ai-1\",\"title\":\"Backend\",\"company\":\"Acme\",\"location\":\"Remote\",\"description\":\"Build\",\"requiredSkills\":[\"Kotlin\"],\"roleKeywords\":[\"backend\"],\"source\":\"ai\"}]" }
                            ]
                          }
                        }
                      ]
                    }
                    """.trimIndent()
                )
        )

        val jobs = geminiService.generateJobsForSearch(
            JobSearchRequest(
                skills = listOf("Kotlin"),
                location = "Remote",
                roleKeywords = listOf("backend")
            )
        )

        assertEquals(1, jobs.size)
        assertEquals("ai-1", jobs.first().id)
    }

    @Test
    fun `generateJobsForSearch returns empty when response is invalid`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "candidates": [
                        {
                          "content": {
                            "parts": [
                              { "text": "not a json array" }
                            ]
                          }
                        }
                      ]
                    }
                    """.trimIndent()
                )
        )

        val jobs = geminiService.generateJobsForSearch(JobSearchRequest(skills = listOf("Kotlin")))

        assertTrue(jobs.isEmpty())
    }

    @Test
    fun `generateMatchReasoning falls back when Gemini response has no candidates`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{}")
        )

        val reasoning = geminiService.generateMatchReasoning(
            cvOrProfile = "Profile text",
            job = JobItem(
                id = "job-1",
                title = "Backend Engineer",
                company = "Acme",
                location = "Remote",
                description = "Build APIs",
                requiredSkills = listOf("Kotlin")
            ),
            overlapPercent = 50,
            missingSkills = listOf("SQL")
        )

        assertEquals("Reasoning unavailable", reasoning)
    }

    @Test
    fun `generateSummary falls back when Gemini returns server error`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":\"boom\"}")
        )

        val summary = geminiService.generateSummary(
            Profile(
                name = "Casey",
                skills = listOf("Spring Boot"),
                experience = "5 years",
                education = "MS"
            )
        )

        assertEquals("Professional Profile", summary.headline)
        assertEquals("Failed to generate summary", summary.summary)
    }
}
