package com.skill2career.adapter.jobboard

import com.skill2career.application.job.JobSource
import com.skill2career.domain.job.JobPosting
import com.skill2career.domain.job.SourceType
import com.skill2career.domain.job.TrackedCompany
import com.skill2career.domain.job.Workplace
import org.jsoup.parser.Parser

/** Greenhouse's public job board. Its descriptions are html, escaped, and only sent when asked for. */
class GreenhouseSource(
    settings: JobBoardSettings = JobBoardSettings(BOARDS),
) : JobSource {
    private val board = BoardHttp(settings)

    override val type = SourceType.GREENHOUSE

    override fun fetch(company: TrackedCompany): List<JobPosting> =
        board.get("/v1/boards/{token}/jobs?content=true", company.boardToken).path("jobs").jobs(company.boardToken).mapNotNull {
            val location = it.path("location").text("name")
            postingOrNull(
                externalId = it.text("id"),
                title = it.text("title"),
                location = location,
                description = PlainText.of(Parser.unescapeEntities(it.text("content").orEmpty(), false)),
                applyUrl = it.text("absolute_url"),
                workplace = workplace(location),
            )
        }

    /**
     * Greenhouse has no field for it, so only a location offering remote work says anything: "Remote,
     * Poland", "London or Remote (UK)", or Cloudflare's "Distributed". Any other location says nothing.
     */
    private fun workplace(location: String?): Workplace? = Workplace.REMOTE.takeIf { location != null && REMOTE.containsMatchIn(location) }

    companion object {
        const val BOARDS = "https://boards-api.greenhouse.io"

        private val REMOTE = Regex("\\b(remote|distributed)\\b", RegexOption.IGNORE_CASE)
    }
}
