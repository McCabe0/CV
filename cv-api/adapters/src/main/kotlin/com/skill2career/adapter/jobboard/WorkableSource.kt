package com.skill2career.adapter.jobboard

import com.skill2career.application.job.JobSource
import com.skill2career.domain.job.JobPosting
import com.skill2career.domain.job.SourceType
import com.skill2career.domain.job.TrackedCompany
import com.skill2career.domain.job.Workplace
import tools.jackson.databind.JsonNode

/** Workable's public widget. Its descriptions are html, only sent when asked for. */
class WorkableSource(
    settings: JobBoardSettings = JobBoardSettings(BOARDS),
) : JobSource {
    private val board = BoardHttp(settings)

    override val type = SourceType.WORKABLE

    override fun fetch(company: TrackedCompany): List<JobPosting> =
        board.get("/api/v1/widget/accounts/{token}?details=true", company.boardToken).path("jobs").jobs(company.boardToken).mapNotNull {
            postingOrNull(
                externalId = it.text("shortcode"),
                title = it.text("title"),
                location = location(it),
                description = PlainText.of(it.text("description").orEmpty()),
                applyUrl = it.text("url"),
                // Workable only says when a job is remote; otherwise it could be hybrid or on site.
                workplace = if (it.path("telecommuting").asBoolean(false)) Workplace.REMOTE else null,
            )
        }

    /** "Vienna, Austria", with "(remote)" after it when the job is remote. */
    private fun location(job: JsonNode): String? {
        val place = listOfNotNull(job.text("city"), job.text("country")).joinToString(", ").ifEmpty { null }
        val remote = job.path("telecommuting").asBoolean(false)
        return when {
            remote && place != null -> "$place (remote)"
            remote -> "Remote"
            else -> place
        }
    }

    companion object {
        const val BOARDS = "https://apply.workable.com"
    }
}
