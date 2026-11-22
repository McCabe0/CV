package com.skill2career.adapter.jobboard

import com.skill2career.application.job.JobSource
import com.skill2career.domain.job.JobPosting
import com.skill2career.domain.job.SourceType
import com.skill2career.domain.job.TrackedCompany
import org.jsoup.parser.Parser

/** Greenhouse's public job board. Its descriptions are html, escaped, and only sent when asked for. */
class GreenhouseSource(
    settings: JobBoardSettings = JobBoardSettings(BOARDS),
) : JobSource {
    private val board = BoardHttp(settings)

    override val type = SourceType.GREENHOUSE

    override fun fetch(company: TrackedCompany): List<JobPosting> =
        board.get("/v1/boards/{token}/jobs?content=true", company.boardToken).path("jobs").jobs(company.boardToken).mapNotNull {
            postingOrNull(
                externalId = it.text("id"),
                title = it.text("title"),
                location = it.path("location").text("name"),
                description = PlainText.of(Parser.unescapeEntities(it.text("content").orEmpty(), false)),
                applyUrl = it.text("absolute_url"),
            )
        }

    companion object {
        const val BOARDS = "https://boards-api.greenhouse.io"
    }
}
