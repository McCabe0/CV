package com.skill2career.adapter.jobboard

import com.skill2career.application.job.JobSource
import com.skill2career.domain.job.JobPosting
import com.skill2career.domain.job.SourceType
import com.skill2career.domain.job.TrackedCompany

/** Ashby's public job board. It can hold jobs the company has not listed, which are left out. */
class AshbySource(
    settings: JobBoardSettings = JobBoardSettings(BOARDS),
) : JobSource {
    private val board = BoardHttp(settings)

    override val type = SourceType.ASHBY

    override fun fetch(company: TrackedCompany): List<JobPosting> =
        board
            .get("/posting-api/job-board/{token}", company.boardToken)
            .path("jobs")
            .jobs(company.boardToken)
            .filter { it.path("isListed").asBoolean(true) }
            .mapNotNull {
                postingOrNull(
                    externalId = it.text("id"),
                    title = it.text("title"),
                    location = it.text("location"),
                    description = PlainText.tidy(it.text("descriptionPlain").orEmpty()),
                    applyUrl = it.text("jobUrl"),
                    // Not isRemote, which ashby also sets for hybrid jobs.
                    workplace = workplaceOf(it.text("workplaceType")),
                )
            }

    companion object {
        const val BOARDS = "https://api.ashbyhq.com"
    }
}
