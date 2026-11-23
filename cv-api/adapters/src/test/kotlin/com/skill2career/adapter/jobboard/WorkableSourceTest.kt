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

/** Workable's widget api: apply.workable.com/api/v1/widget/accounts/{token}?details=true */
class WorkableSourceTest : JobSourceContract() {
    override val type = SourceType.WORKABLE

    override fun source(baseUrl: String): JobSource = WorkableSource(JobBoardSettings(baseUrl))

    private fun path(token: String) = "/api/v1/widget/accounts/$token"

    private fun workable(
        shortcode: String,
        title: String?,
        city: String?,
        country: String?,
        description: String,
        url: String,
        telecommuting: Boolean = false,
    ) = mapOf(
        "shortcode" to shortcode,
        "title" to title,
        "city" to city,
        "country" to country,
        "telecommuting" to telecommuting,
        "description" to "<p>$description</p>",
        "url" to url,
        "application_url" to "$url/apply",
    )

    override fun lists(
        token: String,
        postings: List<JobPosting>,
    ) = board.serves(
        path(token),
        mapOf(
            "name" to "Acme",
            "jobs" to
                postings.map {
                    val remote = it.workplace == Workplace.REMOTE
                    workable(it.externalId, it.title, it.location, null, it.description, it.applyUrl, telecommuting = remote)
                },
        ),
    )

    /** Workable only says when a job is remote: not remote could be hybrid or on site. */
    override val workplacesSaid = listOf(Workplace.REMOTE)

    override fun listsUntitled(token: String) =
        board.serves(path(token), mapOf("jobs" to listOf(workable("1", null, null, null, "", "https://apply.workable.com/j/1"))))

    override fun respondsWith(
        token: String,
        status: Int,
    ) = board.answers(path(token), status)

    @Test
    fun `asks for each job's description, which workable leaves out by default`() {
        lists(COMPANY.boardToken, emptyList())

        source(board.baseUrl()).fetch(COMPANY)

        board.verify(getRequestedFor(urlPathEqualTo(path(COMPANY.boardToken))).withQueryParam("details", equalTo("true")))
    }

    @Test
    fun `gives the location as city and country, and says when the job is remote`() {
        board.serves(
            path(COMPANY.boardToken),
            mapOf(
                "jobs" to
                    listOf(
                        workable("A", "Technician", "Vienna", "Austria", "Fix things.", "https://apply.workable.com/j/A"),
                        workable("B", "Engineer", null, "Greece", "Build things.", "https://apply.workable.com/j/B", telecommuting = true),
                    ),
            ),
        )

        assertThat(source(board.baseUrl()).fetch(COMPANY).map { it.location })
            .containsExactly("Vienna, Austria", "Greece (remote)")
    }
}
