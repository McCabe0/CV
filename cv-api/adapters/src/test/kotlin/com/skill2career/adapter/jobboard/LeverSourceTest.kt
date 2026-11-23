package com.skill2career.adapter.jobboard

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.skill2career.adapter.jobboard.BoardJson.answers
import com.skill2career.adapter.jobboard.BoardJson.serves
import com.skill2career.application.job.JobSource
import com.skill2career.domain.job.JobFixtures.COMPANY
import com.skill2career.domain.job.JobPosting
import com.skill2career.domain.job.SourceType
import com.skill2career.domain.job.Workplace
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

/** Lever's postings api: api.lever.co/v0/postings/{token}?mode=json */
class LeverSourceTest : JobSourceContract() {
    override val type = SourceType.LEVER

    override fun source(baseUrl: String): JobSource = LeverSource(JobBoardSettings(baseUrl))

    private fun path(token: String) = "/v0/postings/$token"

    private fun lever(
        id: String,
        text: String?,
        location: String?,
        descriptionPlain: String,
        hostedUrl: String,
        lists: List<Map<String, String>> = emptyList(),
        additionalPlain: String = "",
        workplaceType: String = "unspecified",
    ) = mapOf(
        "id" to id,
        "text" to text,
        "workplaceType" to workplaceType,
        "categories" to mapOf("location" to location, "team" to "Platform"),
        "descriptionPlain" to descriptionPlain,
        "lists" to lists,
        "additionalPlain" to additionalPlain,
        "hostedUrl" to hostedUrl,
        "applyUrl" to "$hostedUrl/apply",
    )

    override fun lists(
        token: String,
        postings: List<JobPosting>,
    ) = board.serves(
        path(token),
        postings.map {
            val workplaceType = it.workplace?.name?.lowercase() ?: "unspecified"
            lever(it.externalId, it.title, it.location, it.description, it.applyUrl, workplaceType = workplaceType)
        },
    )

    override val workplacesSaid = listOf(Workplace.REMOTE, Workplace.HYBRID, Workplace.ONSITE)

    override fun listsUntitled(token: String) =
        board.serves(path(token), listOf(lever("1", null, null, "", "https://jobs.lever.co/acme/1")))

    override fun respondsWith(
        token: String,
        status: Int,
    ) = board.answers(path(token), status)

    @Test
    fun `asks for json`() {
        lists(COMPANY.boardToken, emptyList())

        source(board.baseUrl()).fetch(COMPANY)

        board.verify(getRequestedFor(urlPathEqualTo(path(COMPANY.boardToken))).withQueryParam("mode", equalTo("json")))
    }

    @Test
    fun `gives the whole description, the opening, each list under its heading, and the closing`() {
        board.serves(
            path(COMPANY.boardToken),
            listOf(
                lever(
                    id = "2a55",
                    text = "Account Executive",
                    location = "New York, NY",
                    descriptionPlain = "We sell software.",
                    hostedUrl = "https://jobs.lever.co/spotify/2a55",
                    lists = listOf(mapOf("text" to "What You'll Do", "content" to "<li>Find customers</li><li>Close deals</li>")),
                    additionalPlain = "The base range is $87k.",
                ),
            ),
        )

        val description = source(board.baseUrl()).fetch(COMPANY).single().description

        assertThat(description).isEqualTo("We sell software.\nWhat You'll Do\nFind customers\nClose deals\nThe base range is $87k.")
    }
}
