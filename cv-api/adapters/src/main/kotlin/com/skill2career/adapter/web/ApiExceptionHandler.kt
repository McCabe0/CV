package com.skill2career.adapter.web

import com.skill2career.adapter.jobboard.JobBoardUnavailable
import com.skill2career.application.cv.CvRequestNotFound
import com.skill2career.application.job.CompanyAlreadyTracked
import com.skill2career.application.job.CompanyNotTracked
import com.skill2career.application.job.FitSummaryNotFound
import com.skill2career.application.job.JobNotFound
import com.skill2career.application.job.NoJobSource
import com.skill2career.application.job.TrackedCompanyNotFound
import com.skill2career.application.profile.ProfileNotFound
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/** The application's exceptions as http problems. Spring's own (validation, bad json) as its defaults. */
@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {
    @ExceptionHandler(ProfileNotFound::class)
    fun notFound(): ProblemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "You have no profile yet.")

    /** Another candidate's request is not found either, so its existence is not given away. */
    @ExceptionHandler(CvRequestNotFound::class)
    fun noCvRequest(): ProblemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No such CV request.")

    @ExceptionHandler(JobNotFound::class)
    fun noJob(): ProblemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No such job, or it is no longer listed.")

    @ExceptionHandler(FitSummaryNotFound::class)
    fun noFitSummary(): ProblemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "You have not asked why this job fits.")

    @ExceptionHandler(TrackedCompanyNotFound::class)
    fun noCompany(): ProblemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No such company.")

    @ExceptionHandler(CompanyAlreadyTracked::class)
    fun alreadyTracked(): ProblemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "This board is already tracked.")

    @ExceptionHandler(CompanyNotTracked::class)
    fun notTracked(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "This company is no longer tracked. Track it again to crawl it.")

    @ExceptionHandler(NoJobSource::class)
    fun noSource(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, "Boards of this kind cannot be read.")

    /** The board is there, but did not answer properly just now: the admin can try again. */
    @ExceptionHandler(JobBoardUnavailable::class)
    fun boardUnavailable(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "The board could not be read just now. Try again in a minute.")
}
