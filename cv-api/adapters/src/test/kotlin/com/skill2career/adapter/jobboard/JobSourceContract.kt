package com.skill2career.adapter.jobboard

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.any
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.github.tomakehurst.wiremock.http.Fault
import com.skill2career.application.job.JobSource
import com.skill2career.domain.job.JobFixtures.COMPANY
import com.skill2career.domain.job.JobPosting
import com.skill2career.domain.job.SourceType
import com.skill2career.domain.job.TrackedCompany
import com.skill2career.domain.job.Workplace
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test

/**
 * What every job board reader must do, whichever applicant tracking system it reads. Each board's test
 * says how its board looks on the wire, with wiremock playing it, and inherits these tests. See
 * decisions/jobs.md.
 */
abstract class JobSourceContract {
    protected val board = WireMockServer(options().dynamicPort())

    abstract val type: SourceType

    /** The reader under test, reading boards at this url. */
    abstract fun source(baseUrl: String): JobSource

    /** The board for [token] lists these postings, in its own format. A description is given as plain text. */
    abstract fun lists(
        token: String,
        postings: List<JobPosting>,
    )

    /**
     * Where a job can be done, in the words this board has for it. [lists] writes a posting's workplace in
     * the board's own way; one with none, the way the board says nothing.
     */
    abstract val workplacesSaid: List<Workplace>

    /** The board for [token] lists one posting this reader cannot use: with no title. */
    abstract fun listsUntitled(token: String)

    /** The board for [token] answers with this http status. */
    abstract fun respondsWith(
        token: String,
        status: Int,
    )

    private val company: TrackedCompany get() = COMPANY.copy(source = type, boardToken = "acme")

    private val posting =
        JobPosting(
            externalId = "4001",
            title = "Backend Engineer",
            location = "London",
            description = "Build payment APIs in Kotlin.",
            applyUrl = "https://jobs.example.com/acme/4001",
        )

    @BeforeEach
    fun start() = board.start()

    @AfterEach
    fun stop() = board.stop()

    private fun fetch() = source(board.baseUrl()).fetch(company)

    @Test
    fun `reads the type of board it is for`() {
        assertThat(source(board.baseUrl()).type).isEqualTo(type)
    }

    @Test
    fun `returns what the company's board lists, its description as plain text`() {
        val second = posting.copy(externalId = "4002", title = "Data Engineer", location = null)
        lists("acme", listOf(posting, second))

        assertThat(fetch()).containsExactly(posting, second)
    }

    @Test
    fun `says where each job is done when its board does, and nothing when it does not`() {
        val said = workplacesSaid.mapIndexed { i, workplace -> posting.copy(externalId = "${5000 + i}", workplace = workplace) }
        lists("acme", said + posting.copy(workplace = null))

        assertThat(fetch().map { it.workplace }).containsExactly(*(workplacesSaid + null).toTypedArray())
    }

    @Test
    fun `returns nothing for a board with no jobs`() {
        lists("acme", emptyList())

        assertThat(fetch()).isEmpty()
    }

    @Test
    fun `skips a posting it cannot use, rather than losing the whole board`() {
        listsUntitled("acme")

        assertThat(fetch()).isEmpty()
    }

    @Test
    fun `an unknown board is not retried, as it will not appear`() {
        respondsWith("acme", 404)

        assertThatThrownBy { fetch() }.isInstanceOf(UnknownJobBoard::class.java)
    }

    @Test
    fun `is unavailable when rate limited, so the crawl is retried`() {
        respondsWith("acme", 429)

        assertThatThrownBy { fetch() }.isInstanceOf(JobBoardUnavailable::class.java)
    }

    @Test
    fun `is unavailable when the board fails`() {
        respondsWith("acme", 503)

        assertThatThrownBy { fetch() }.isInstanceOf(JobBoardUnavailable::class.java)
    }

    @Test
    fun `is unavailable when the board cannot be reached`() {
        board.stubFor(any(anyUrl()).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)))

        assertThatThrownBy { fetch() }.isInstanceOf(JobBoardUnavailable::class.java)
    }

    @Test
    fun `is unavailable when the board answers with something that is not its json`() {
        board.stubFor(any(anyUrl()).willReturn(aResponse().withHeader("Content-Type", "text/html").withBody("<html>")))

        assertThatThrownBy { fetch() }.isInstanceOf(JobBoardUnavailable::class.java)
    }

    @Test
    fun `is unavailable, not empty, when the board's json has no list of jobs, so none are marked inactive`() {
        board.stubFor(
            any(anyUrl()).willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""{"error": "moved"}""")),
        )

        assertThatThrownBy { fetch() }.isInstanceOf(JobBoardUnavailable::class.java)
    }
}
