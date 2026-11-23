package com.skill2career.adapter.web.cv

import com.skill2career.domain.cv.Answer
import com.skill2career.domain.cv.Cv
import com.skill2career.domain.cv.CvRequest
import com.skill2career.domain.cv.CvStatus
import com.skill2career.domain.cv.Issue
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size

data class QuestionResponse(
    val id: Int,
    val text: String,
)

data class AnswerResponse(
    val questionId: Int,
    @field:Schema(description = "Empty when skipped.")
    val text: String?,
)

@Schema(description = "One turn of the writer and the critic: the critic's review, and the draft's summary.")
data class RoundResponse(
    val number: Int,
    val score: Int,
    val approved: Boolean,
    val issues: List<Issue>,
    val summary: String,
)

@Schema(description = "A CV request: the interviewer's questions, each round, and the finished CV once completed.")
data class CvRequestResponse(
    val id: Long,
    val status: CvStatus,
    val questions: List<QuestionResponse>,
    val answers: List<AnswerResponse>,
    val rounds: List<RoundResponse>,
    @field:Schema(description = "Only once completed.")
    val cv: Cv?,
    @field:Schema(description = "Only once failed: why.")
    val failure: String?,
    @field:Schema(description = "The job this CV is aimed at; none for a general CV.")
    val jobId: Long? = null,
) {
    companion object {
        fun of(request: CvRequest) =
            CvRequestResponse(
                jobId = request.jobId,
                id = request.id!!,
                status = request.status,
                questions = request.questions.map { QuestionResponse(it.id, it.text) },
                answers = request.answers.map { AnswerResponse(it.questionId, it.text) },
                rounds =
                    request.rounds.map {
                        RoundResponse(it.number, it.review.score, it.review.approved, it.review.issues, it.draft.summary)
                    },
                cv = request.cv,
                failure = request.failure,
            )
    }
}

@Schema(description = "What to aim the CV at. No body, or no jobId, for a general CV.")
data class CvRequestBody(
    @field:Schema(description = "A listed job, from your matches.")
    val jobId: Long? = null,
)

data class AnswerRequest(
    @field:Min(1)
    val questionId: Int,
    // Long enough for a detailed answer. The ui counts down to it.
    @field:Size(max = 2000)
    @field:Schema(description = "Leave out, or empty, to skip the question.")
    val text: String? = null,
)

@Schema(description = "The candidate's answers. A question left out is skipped.")
data class AnswersRequest(
    @field:Valid @field:Size(max = 5)
    val answers: List<AnswerRequest> = emptyList(),
) {
    fun toDomain() = answers.map { Answer(it.questionId, it.text) }
}
