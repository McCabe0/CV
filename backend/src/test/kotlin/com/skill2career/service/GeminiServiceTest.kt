package com.skill2career.service

import com.skill2career.model.JobItem
import com.skill2career.model.Profile
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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
    fun `generateSummary returns generated text from Gemini response`() {
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
                              { "text": "Generated CV summary" }
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
        assertEquals("Generated CV summary", summary)
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

        assertEquals("Failed to generate summary", summary)
    }
}
