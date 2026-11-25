package com.skill2career.app.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.skill2career.application.job.CrawlResults
import com.skill2career.application.job.JobStore
import com.skill2career.application.job.TrackedCompanyStore
import com.skill2career.domain.job.CrawlOutcome
import com.skill2career.domain.job.SourceType
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import java.time.Duration
import kotlin.test.Test

/**
 * The admin zone's api, as an admin uses it: their real token, the api reading the new board (wiremock,
 * playing greenhouse), and the job-worker crawling it through kafka into postgres.
 */
@TestPropertySource(properties = ["app.roles=api,job-worker", "outbox.relay-delay-ms=100"])
class CompanyAdminIntegrationTest : KeycloakIntegrationBase() {
    companion object {
        private val boards = WireMockServer(options().dynamicPort()).apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun boardUrls(registry: DynamicPropertyRegistry) {
            listOf("greenhouse", "lever", "ashby", "workable").forEach { board ->
                registry.add("jobs.boards.$board-url") { boards.baseUrl() }
            }
        }

        private const val STRIPE_JOBS =
            """{"jobs": [
                {"id": 1, "title": "Backend Engineer", "location": {"name": "Dublin"}, "content": "Kotlin.",
                 "absolute_url": "https://stripe.com/jobs/1"},
                {"id": 2, "title": "Data Engineer", "location": {"name": "Remote"}, "content": "SQL.",
                 "absolute_url": "https://stripe.com/jobs/2"}
            ]}"""
    }

    @Autowired
    lateinit var companies: TrackedCompanyStore

    @Autowired
    lateinit var jobs: JobStore

    @Autowired
    lateinit var results: CrawlResults

    private fun stripe() = companies.findByBoard(SourceType.GREENHOUSE, "stripe")!!

    private fun liveJobs() = jobs.forCompany(stripe().id!!).count { it.active }

    @Test
    fun `an admin adds a company, sees it crawled, crawls it again, stops tracking it, and tracks it again`() {
        boards.stubFor(get(urlPathEqualTo("/v1/boards/stripe/jobs")).willReturn(okJson(STRIPE_JOBS)))

        postJson("/admin/companies", mapOf("name" to "Stripe", "source" to "GREENHOUSE", "boardToken" to "stripe"), ADMIN)
            .expectStatus()
            .isCreated
            .expectBody()
            .jsonPath("$.tracked")
            .isEqualTo(true)

        // Its first crawl was queued with it, and the job-worker has done it.
        await().atMost(Duration.ofSeconds(60)).until { liveJobs() == 2 && results.latest()[stripe().id]?.found == 2 }
        get("/admin/companies", ADMIN)
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$[?(@.boardToken == 'stripe')].liveJobs")
            .isEqualTo(2)
            .jsonPath("$[?(@.boardToken == 'stripe')].lastCrawl.outcome")
            .isEqualTo("WORKED")

        post("/admin/companies/${stripe().id}/crawl", ADMIN).expectStatus().isAccepted

        delete("/admin/companies/${stripe().id}", ADMIN)
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.tracked")
            .isEqualTo(false)
        assertThat(liveJobs()).isZero()
        post("/admin/companies/${stripe().id}/crawl", ADMIN).expectStatus().isEqualTo(409)

        postJson("/admin/companies", mapOf("name" to "Stripe Inc", "source" to "GREENHOUSE", "boardToken" to "stripe"), ADMIN)
            .expectStatus()
            .isCreated
        assertThat(stripe().name).isEqualTo("Stripe Inc")
        await().atMost(Duration.ofSeconds(60)).until { liveJobs() == 2 }
    }

    @Test
    fun `a board that does not exist is refused, and nothing is tracked`() {
        postJson("/admin/companies", mapOf("name" to "Nobody", "source" to "LEVER", "boardToken" to "no-such-board"), ADMIN)
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.detail")
            .isEqualTo("There is no LEVER board called 'no-such-board'. Check the board token.")

        assertThat(companies.findByBoard(SourceType.LEVER, "no-such-board")).isNull()
    }

    @Test
    fun `a board already tracked is refused`() {
        postJson("/admin/companies", mapOf("name" to "Monzo", "source" to "GREENHOUSE", "boardToken" to "monzo"), ADMIN)
            .expectStatus()
            .isEqualTo(409)
    }

    @Test
    fun `a candidate cannot see or change the companies`() {
        get("/admin/companies", CANDIDATE).expectStatus().isForbidden
        postJson("/admin/companies", mapOf("name" to "Stripe", "source" to "GREENHOUSE", "boardToken" to "stripe"), CANDIDATE)
            .expectStatus()
            .isForbidden
    }
}
