package com.skill2career.adapter.jobboard

import com.skill2career.application.job.JobSource
import com.skill2career.domain.job.JobPosting
import com.skill2career.domain.job.SourceType
import com.skill2career.domain.job.TrackedCompany
import tools.jackson.databind.JsonNode

/**
 * Lever's public postings. Its list of jobs is the whole answer. A description comes in parts: an
 * opening, lists under headings (in html), and a closing, so they are put back together.
 */
class LeverSource(
    settings: JobBoardSettings = JobBoardSettings(BOARDS),
) : JobSource {
    private val board = BoardHttp(settings)

    override val type = SourceType.LEVER

    override fun fetch(company: TrackedCompany): List<JobPosting> =
        board.get("/v0/postings/{token}?mode=json", company.boardToken).jobs(company.boardToken).mapNotNull {
            postingOrNull(
                externalId = it.text("id"),
                title = it.text("text"),
                location = it.path("categories").text("location"),
                description = description(it),
                applyUrl = it.text("hostedUrl"),
            )
        }

    private fun description(posting: JsonNode): String {
        val lists = posting.path("lists").values().flatMap { listOfNotNull(it.text("text"), it.text("content")?.let(PlainText::of)) }
        val parts = listOfNotNull(posting.text("descriptionPlain")) + lists + listOfNotNull(posting.text("additionalPlain"))
        return PlainText.tidy(parts.joinToString("\n"))
    }

    companion object {
        const val BOARDS = "https://api.lever.co"
    }
}
