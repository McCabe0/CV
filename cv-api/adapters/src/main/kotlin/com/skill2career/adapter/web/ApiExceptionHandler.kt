package com.skill2career.adapter.web

import com.skill2career.application.cv.CvRequestNotFound
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
}
