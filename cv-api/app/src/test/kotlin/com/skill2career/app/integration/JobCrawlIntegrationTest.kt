package com.skill2career.app.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.skill2career.adapter.messaging.CrawlScheduler
import com.skill2career.application.job.CrawlQueue
import com.skill2career.application.job.JobStore
import com.skill2career.application.job.TrackedCompanyStore
import com.skill2career.domain.job.TrackedCompany
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import kotlin.test.Test

/**
 * The whole crawl, as it runs: the job-worker's schedule queues a crawl per company in the outbox, the
 * relay sends them to kafka, and the job-worker reads each company's board (wiremock here, playing all
 * four) into postgres. Retries are quick, so a failing board's path can be seen in the time a test takes.
 */
@TestPropertySource(
    properties = [
        "app.roles=job-worker",
        "jobs.crawl.attempts=2",
        "jobs.crawl.retry-delay-ms=300",
        "outbox.relay-delay-ms=100",
    ],
)
class JobCrawlIntegrationTest : KeycloakIntegrationBase() {
    companion object {
        private val boards = WireMockServer(options().dynamicPort()).apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun boardUrls(registry: DynamicPropertyRegistry) {
            listOf("greenhouse", "lever", "ashby", "workable").forEach { board ->
                registry.add("jobs.boards.$board-url") { boards.baseUrl() }
            }
        }
    }

    @Autowired
    lateinit var scheduler: CrawlScheduler

    @Autowired
    lateinit var queue: CrawlQueue

    @Autowired
    lateinit var companies: TrackedCompanyStore

    @Autowired
    lateinit var jobs: JobStore

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private val json = JsonMapper.builder().build()

    private fun company(name: String): TrackedCompany = companies.all().single { it.name == name }

    private fun activeTitles(name: String) =
        jobs
            .forCompany(company(name).id!!)
            .filter { it.active }
            .map { it.posting.title }
            .sorted()

    /** Every starting company's board lists nothing, until a test says otherwise. */
    @BeforeEach
    fun emptyBoards() {
        boards.resetAll()
        listOf("monzo", "gitlab", "cloudflare").forEach { greenhouse(it) }
        listOf("palantir", "spotify").forEach { boards.stubFor(get(urlPathEqualTo("/v0/postings/$it")).willReturn(okJson("[]"))) }
        listOf("ramp", "linear").forEach {
            boards.stubFor(get(urlPathEqualTo("/posting-api/job-board/$it")).willReturn(okJson("""{"jobs": []}""")))
        }
        boards.stubFor(get(urlPathEqualTo("/api/v1/widget/accounts/blueground")).willReturn(okJson("""{"jobs": []}""")))
    }

    /** Greenhouse lists these titles for [token], each its own id. */
    private fun greenhouse(
        token: String,
        vararg titles: String,
    ) {
        val listed =
            titles.mapIndexed { i, title ->
                mapOf(
                    "id" to 1000 + i,
                    "title" to title,
                    "location" to mapOf("name" to "London"),
                    "content" to "&lt;p&gt;$title, in Kotlin.&lt;/p&gt;",
                    "absolute_url" to "https://job-boards.greenhouse.io/$token/jobs/${1000 + i}",
                )
            }
        boards.stubFor(get(urlPathEqualTo("/v1/boards/$token/jobs")).willReturn(okJson(json.writeValueAsString(mapOf("jobs" to listed)))))
    }

    private fun requestsTo(path: String) = boards.findAll(getRequestedFor(urlPathEqualTo(path))).size

    @Test
    fun `the day's schedule crawls every tracked company's board, once a day, into our copy of their jobs`() {
        jdbc.update("DELETE FROM crawl_runs")
        greenhouse("monzo", "Backend Engineer", "Android Engineer")

        scheduler.run()
        scheduler.run()

        await().atMost(Duration.ofSeconds(60)).untilAsserted {
            assertThat(activeTitles("Monzo")).containsExactly("Android Engineer", "Backend Engineer")
            assertThat(requestsTo("/api/v1/widget/accounts/blueground")).isEqualTo(1)
        }
        val backend = jobs.forCompany(company("Monzo").id!!).single { it.posting.title == "Backend Engineer" }
        assertThat(backend.posting.description).isEqualTo("Backend Engineer, in Kotlin.")
        assertThat(backend.posting.applyUrl).isEqualTo("https://job-boards.greenhouse.io/monzo/jobs/1000")
        assertThat(requestsTo("/v1/boards/monzo/jobs")).isEqualTo(1)
    }

    @Test
    fun `a job its board stops listing is marked inactive at the next crawl`() {
        greenhouse("cloudflare", "Network Engineer", "Account Executive")
        queue.queue(company("Cloudflare"))
        await().atMost(Duration.ofSeconds(60)).until { activeTitles("Cloudflare").size == 2 }

        greenhouse("cloudflare", "Network Engineer")
        queue.queue(company("Cloudflare"))

        await().atMost(Duration.ofSeconds(60)).until { activeTitles("Cloudflare") == listOf("Network Engineer") }
        assertThat(jobs.forCompany(company("Cloudflare").id!!).single { !it.active }.inactiveSince).isNotNull()
    }

    @Test
    fun `a board that keeps failing is tried, then parked, its jobs left as they were`() {
        greenhouse("gitlab", "Site Reliability Engineer")
        queue.queue(company("GitLab"))
        await().atMost(Duration.ofSeconds(60)).until { activeTitles("GitLab") == listOf("Site Reliability Engineer") }
        boards.stubFor(get(urlPathEqualTo("/v1/boards/gitlab/jobs")).willReturn(aResponse().withStatus(503)))
        val before = requestsTo("/v1/boards/gitlab/jobs")

        queue.queue(company("GitLab"))

        await().atMost(Duration.ofSeconds(60)).until { requestsTo("/v1/boards/gitlab/jobs") == before + 2 }
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).until { requestsTo("/v1/boards/gitlab/jobs") == before + 2 }
        assertThat(activeTitles("GitLab")).containsExactly("Site Reliability Engineer")
    }

    @Test
    fun `a board that does not exist is not retried`() {
        boards.stubFor(get(urlPathEqualTo("/v0/postings/palantir")).willReturn(aResponse().withStatus(404)))

        queue.queue(company("Palantir"))

        await().atMost(Duration.ofSeconds(60)).until { requestsTo("/v0/postings/palantir") == 1 }
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).until { requestsTo("/v0/postings/palantir") == 1 }
    }
}
