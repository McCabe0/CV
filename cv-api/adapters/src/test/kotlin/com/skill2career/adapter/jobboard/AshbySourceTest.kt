package com.skill2career.adapter.jobboard

import com.skill2career.adapter.jobboard.BoardJson.answers
import com.skill2career.adapter.jobboard.BoardJson.serves
import com.skill2career.application.job.JobSource
import com.skill2career.domain.job.JobFixtures.COMPANY
import com.skill2career.domain.job.JobPosting
import com.skill2career.domain.job.SourceType
import com.skill2career.domain.job.Workplace
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

/** Ashby's posting api: api.ashbyhq.com/posting-api/job-board/{token} */
class AshbySourceTest : JobSourceContract() {
    override val type = SourceType.ASHBY

    override fun source(baseUrl: String): JobSource = AshbySource(JobBoardSettings(baseUrl))

    private fun path(token: String) = "/posting-api/job-board/$token"

    private fun ashby(
        id: String,
        title: String?,
        location: String?,
        descriptionPlain: String,
        jobUrl: String,
        isListed: Boolean = true,
        workplaceType: String? = null,
    ) = mapOf(
        "id" to id,
        "title" to title,
        "location" to location,
        "isListed" to isListed,
        "workplaceType" to workplaceType,
        // As ashby sends it: true for hybrid jobs too.
        "isRemote" to (workplaceType == "Remote" || workplaceType == "Hybrid"),
        "descriptionPlain" to descriptionPlain,
        "descriptionHtml" to "<p>$descriptionPlain</p>",
        "jobUrl" to jobUrl,
        "applyUrl" to "$jobUrl/application",
    )

    override fun lists(
        token: String,
        postings: List<JobPosting>,
    ) = board.serves(
        path(token),
        mapOf(
            "apiVersion" to "1",
            "jobs" to
                postings.map {
                    ashby(
                        it.externalId,
                        it.title,
                        it.location,
                        it.description,
                        it.applyUrl,
                        workplaceType = it.workplace?.let(WORDS::getValue),
                    )
                },
        ),
    )

    override val workplacesSaid = listOf(Workplace.REMOTE, Workplace.HYBRID, Workplace.ONSITE)

    private companion object {
        val WORDS = mapOf(Workplace.REMOTE to "Remote", Workplace.HYBRID to "Hybrid", Workplace.ONSITE to "OnSite")
    }

    override fun listsUntitled(token: String) =
        board.serves(path(token), mapOf("jobs" to listOf(ashby("1", null, null, "", "https://jobs.ashbyhq.com/acme/1"))))

    override fun respondsWith(
        token: String,
        status: Int,
    ) = board.answers(path(token), status)

    @Test
    fun `leaves out a job the company has not listed`() {
        board.serves(
            path(COMPANY.boardToken),
            mapOf(
                "jobs" to
                    listOf(
                        ashby("1", "Listed", "Europe", "Open to all.", "https://jobs.ashbyhq.com/linear/1"),
                        ashby("2", "Unlisted", "Europe", "By invite.", "https://jobs.ashbyhq.com/linear/2", isListed = false),
                    ),
            ),
        )

        assertThat(source(board.baseUrl()).fetch(COMPANY).map { it.title }).containsExactly("Listed")
    }
}
