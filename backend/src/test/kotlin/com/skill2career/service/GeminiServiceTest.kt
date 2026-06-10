package com.skill2career.service

import com.skill2career.model.JobItem
import com.skill2career.model.JobSearchRequest
import com.skill2career.model.Profile
import com.skill2career.model.WorkExperience
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    fun `generateSummary includes structured work history in the prompt`() {
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
                              { "text": "{\"headline\":\"Engineer\",\"summary\":\"s\",\"keySkills\":[\"Kotlin\"],\"experienceBullets\":[\"b\"],\"educationSection\":\"BS\",\"atsKeywords\":[\"Kotlin\"]}" }
                            ]
                          }
                        }
                      ]
                    }
                    """.trimIndent()
                )
        )

        geminiService.generateSummary(
            Profile(
                name = "Jordan",
                skills = listOf("Kotlin"),
                experience = "6 years",
                education = "MS",
                workHistory = listOf(
                    WorkExperience(
                        company = "Acme Corp",
                        title = "Senior Engineer",
                        startDate = "2020",
                        endDate = "2024",
                        bullets = listOf("Led the platform migration")
                    )
                )
            )
        )

        val sentPrompt = server.takeRequest().body.readUtf8()
        assertTrue(sentPrompt.contains("Senior Engineer at Acme Corp"))
        assertTrue(sentPrompt.contains("Led the platform migration"))
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
    fun `generateJobsForSearch returns fallback jobs when response is invalid`() {
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

        // An unparseable AI response now degrades to generated fallback jobs rather than an empty list.
        assertFalse(jobs.isEmpty())
        assertTrue(jobs.all { it.source == "fallback-search" })
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
        // The fallback now surfaces the underlying Gemini failure instead of a generic message.
        assertTrue(summary.summary.contains("Gemini unavailable"))
    }

    @Test
    fun `repeated identical prompts are served from cache without another network call`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(reasoningBody("Cached reasoning"))
        )

        val job = sampleJob()
        val first = geminiService.generateMatchReasoning("Profile", job, 50, listOf("SQL"))
        val second = geminiService.generateMatchReasoning("Profile", job, 50, listOf("SQL"))

        assertEquals("Cached reasoning", first)
        assertEquals(first, second)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `expired cache entries are refetched`() {
        var clock = 0L
        val service = serviceWith(successCacheTtlMillis = 1_000L, currentTimeMillis = { clock })

        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(reasoningBody("First"))
        )
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(reasoningBody("Second"))
        )

        val job = sampleJob()
        val first = service.generateMatchReasoning("Profile", job, 50, listOf("SQL"))
        clock = 5_000L // advance past the cache TTL
        val second = service.generateMatchReasoning("Profile", job, 50, listOf("SQL"))

        assertEquals("First", first)
        assertEquals("Second", second)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `cache evicts an entry once capacity is reached`() {
        val service = serviceWith(maxCacheEntries = 1)

        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(reasoningBody("A"))
        )
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(reasoningBody("B"))
        )

        val job = sampleJob()
        service.generateMatchReasoning("Profile A", job, 50, emptyList())
        service.generateMatchReasoning("Profile B", job, 50, emptyList())

        assertEquals(2, server.requestCount)
    }

    @Test
    fun `executePrompt retries transient errors and then succeeds`() {
        val service = serviceWith(maxRetries = 1, retryBackoffMillis = 1)
        server.enqueue(overloadedResponse())
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(reasoningBody("Recovered"))
        )

        val result = service.generateMatchReasoning("Profile", sampleJob(), 50, listOf("SQL"))

        assertEquals("Recovered", result)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `executePrompt gives up after exhausting retries on transient errors`() {
        val service = serviceWith(maxRetries = 1, retryBackoffMillis = 1)
        server.enqueue(overloadedResponse())
        server.enqueue(overloadedResponse())

        val result = service.generateMatchReasoning("Profile", sampleJob(), 50, listOf("SQL"))

        assertTrue(result.contains("Gemini unavailable"))
        assertEquals(2, server.requestCount)
    }

    private fun serviceWith(
        successCacheTtlMillis: Long = 10 * 60 * 1000L,
        maxCacheEntries: Int = 500,
        maxRetries: Long = 2,
        retryBackoffMillis: Long = 10,
        currentTimeMillis: () -> Long = { System.currentTimeMillis() }
    ): GeminiService {
        val webClient = WebClient.builder()
            .baseUrl(server.url("/").toString().removeSuffix("/"))
            .build()
        return GeminiService(
            webClient,
            "test-api-key",
            successCacheTtlMillis = successCacheTtlMillis,
            maxCacheEntries = maxCacheEntries,
            maxRetries = maxRetries,
            retryBackoffMillis = retryBackoffMillis,
            currentTimeMillis = currentTimeMillis
        )
    }

    private fun overloadedResponse(): MockResponse =
        MockResponse()
            .setResponseCode(503)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"error\":{\"code\":503,\"status\":\"UNAVAILABLE\"}}")

    private fun reasoningBody(text: String): String = """
        {
          "candidates": [
            { "content": { "parts": [ { "text": "$text" } ] } }
          ]
        }
    """.trimIndent()

    private fun sampleJob(): JobItem = JobItem(
        id = "job-1",
        title = "Backend Engineer",
        company = "Acme",
        location = "Remote",
        description = "Build APIs",
        requiredSkills = listOf("Kotlin")
    )
}
