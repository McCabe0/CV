package com.skill2career.adapter.jobboard

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.skill2career.adapter.jobboard.BoardJson.answers
import com.skill2career.adapter.jobboard.BoardJson.escaped
import com.skill2career.adapter.jobboard.BoardJson.serves
import com.skill2career.application.job.JobSource
import com.skill2career.domain.job.JobFixtures.COMPANY
import com.skill2career.domain.job.JobPosting
import com.skill2career.domain.job.SourceType
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

/** Greenhouse's job board api: boards-api.greenhouse.io/v1/boards/{token}/jobs?content=true */
class GreenhouseSourceTest : JobSourceContract() {
    override val type = SourceType.GREENHOUSE

    override fun source(baseUrl: String): JobSource = GreenhouseSource(JobBoardSettings(baseUrl))

    private fun path(token: String) = "/v1/boards/$token/jobs"

    override fun lists(
        token: String,
        postings: List<JobPosting>,
    ) = board.serves(
        path(token),
        mapOf(
            "jobs" to
                postings.map {
                    mapOf(
                        "id" to it.externalId.toLong(),
                        "title" to it.title,
                        "location" to mapOf("name" to it.location),
                        "content" to escaped("<p>${it.description}</p>"),
                        "absolute_url" to it.applyUrl,
                        "departments" to listOf(mapOf("name" to "Engineering")),
                    )
                },
            "meta" to mapOf("total" to postings.size),
        ),
    )

    override fun listsUntitled(token: String) =
        board.serves(
            path(token),
            mapOf("jobs" to listOf(mapOf("id" to 1, "absolute_url" to "https://jobs.example.com/1", "content" to ""))),
        )

    override fun respondsWith(
        token: String,
        status: Int,
    ) = board.answers(path(token), status)

    @Test
    fun `asks for each job's description, which greenhouse leaves out by default`() {
        lists(COMPANY.boardToken, emptyList())

        source(board.baseUrl()).fetch(COMPANY)

        board.verify(getRequestedFor(urlPathEqualTo(path(COMPANY.boardToken))).withQueryParam("content", equalTo("true")))
    }

    @Test
    fun `turns its escaped html description into plain text`() {
        board.serves(
            path(COMPANY.boardToken),
            mapOf(
                "jobs" to
                    listOf(
                        mapOf(
                            "id" to 7,
                            "title" to "Android Engineer",
                            "location" to mapOf("name" to "Barcelona"),
                            "content" to escaped("<h3>About us</h3><p>We&#39;re a bank &amp; more.</p><ul><li>Kotlin</li></ul>"),
                            "absolute_url" to "https://job-boards.greenhouse.io/monzo/jobs/7",
                        ),
                    ),
            ),
        )

        val description = source(board.baseUrl()).fetch(COMPANY).single().description

        assertThat(description).isEqualTo("About us\nWe're a bank & more.\nKotlin")
    }
}
