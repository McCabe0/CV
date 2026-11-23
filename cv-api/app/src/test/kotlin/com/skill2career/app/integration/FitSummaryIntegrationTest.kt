package com.skill2career.app.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.skill2career.application.job.JobStore
import com.skill2career.application.job.TrackedCompanyStore
import com.skill2career.domain.job.Job
import com.skill2career.domain.job.JobPosting
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import com.github.tomakehurst.wiremock.client.WireMock.post as stubPost

/**
 * Why a job fits, as it runs: the api saves the ask and its outbox message, the relay sends it to kafka,
 * the cv-worker asks gemini (wiremock here) and saves the answer, and the candidate reads it. Retries are
 * quick, so the give up path can be seen in the time a test takes.
 */
@TestPropertySource(
    properties = [
        "app.roles=api,cv-worker",
        "jobs.fit.attempts=2",
        "jobs.fit.retry-delay-ms=300",
        "outbox.relay-delay-ms=100",
    ],
)
class FitSummaryIntegrationTest : KeycloakIntegrationBase() {
    companion object {
        private val gemini = WireMockServer(options().dynamicPort()).apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun llm(registry: DynamicPropertyRegistry) {
            registry.add("llm.gemini.base-url") { "${gemini.baseUrl()}/v1beta" }
        }
    }

    @Autowired
    lateinit var jobs: JobStore

    @Autowired
    lateinit var companies: TrackedCompanyStore

    private val json = JsonMapper.builder().build()

    private val profile =
        mapOf(
            "name" to "Casey Candidate",
            "skills" to listOf("Kotlin"),
            "experience" to "Five years building payment APIs.",
            "education" to "BSc Computer Science",
            "workHistory" to emptyList<Any>(),
        )

    @BeforeEach
    fun reset() = gemini.resetAll()

    /** A new listed job, so each test asks about its own. */
    private fun listedJob(): Long {
        val monzo = companies.all().single { it.name == "Monzo" }
        val externalId = "fit-${UUID.randomUUID()}"
        jobs.saveAll(
            listOf(
                Job(
                    companyId = monzo.id!!,
                    source = monzo.source,
                    posting = JobPosting(externalId, "Kotlin Engineer", "London", "Payments in Kotlin.", "https://monzo.com/$externalId"),
                    firstSeenAt = Instant.now(),
                ),
            ),
        )
        return jobs.forCompany(monzo.id!!).single { it.posting.externalId == externalId }.id!!
    }

    private fun geminiSays(summary: String) {
        val text = json.writeValueAsString(mapOf("summary" to summary))
        val body =
            mapOf("candidates" to listOf(mapOf("content" to mapOf("parts" to listOf(mapOf("text" to text))), "finishReason" to "STOP")))
        gemini.stubFor(
            stubPost(urlPathMatching("/v1beta/models/.*:generateContent"))
                .withRequestBody(containing("why a job fits"))
                .willReturn(okJson(json.writeValueAsString(body))),
        )
    }

    // The status, as the candidate reads it, the way the jobs zone checks back.
    private fun statusOf(jobId: Long): String? =
        get("/jobs/$jobId/fit", username = CANDIDATE)
            .expectBody(String::class.java)
            .returnResult()
            .responseBody
            ?.let { json.readTree(it).path("status").asString() }

    private fun savesProfile() {
        put("/profile", profile, username = CANDIDATE).expectStatus().isOk
    }

    @Test
    fun `a candidate asks why a job fits, and reads it once the cv-worker has written it`() {
        savesProfile()
        val jobId = listedJob()
        geminiSays("Your Kotlin payments work is what this role asks for.")

        post("/jobs/$jobId/fit", username = CANDIDATE)
            .expectStatus()
            .isAccepted
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("WRITING")

        await().atMost(Duration.ofSeconds(60)).until { statusOf(jobId) == "READY" }
        get("/jobs/$jobId/fit", username = CANDIDATE)
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.text")
            .isEqualTo("Your Kotlin payments work is what this role asks for.")

        // Asked again, it is given at once, and gemini is not asked a second time.
        post("/jobs/$jobId/fit", username = CANDIDATE).expectStatus().isOk
        assertThat(gemini.findAll(postRequestedFor(urlPathMatching("/v1beta/.*")))).hasSize(1)
    }

    @Test
    fun `a summary the LLM is too busy to write fails after its last try, so the candidate can ask again`() {
        savesProfile()
        val jobId = listedJob()
        gemini.stubFor(stubPost(urlPathMatching("/v1beta/.*")).willReturn(aResponse().withStatus(503)))

        post("/jobs/$jobId/fit", username = CANDIDATE).expectStatus().isAccepted

        await().atMost(Duration.ofSeconds(60)).until { statusOf(jobId) == "FAILED" }
        get("/jobs/$jobId/fit", username = CANDIDATE)
            .expectBody()
            .jsonPath("$.failure")
            .isEqualTo("The AI was too busy to write this. Please ask again later.")
    }
}
