package com.skill2career.adapter.web.cv

import com.skill2career.adapter.web.security.ownerId
import com.skill2career.application.cv.AnswerQuestions
import com.skill2career.application.cv.FindCvRequest
import com.skill2career.application.cv.RequestCv
import com.skill2career.application.profile.ProfileNotFound
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

/**
 * The signed in candidate's CV requests. The work is done by the cv-worker, so a request answers 202 at
 * once, and its progress follows on a stream, or by asking again. See decisions/messaging.md.
 */
@Tag(name = "cv")
@RestController
@RequestMapping("/cv-requests")
@PreAuthorize("hasRole('CANDIDATE')")
class CvRequestResource(
    private val requestCv: RequestCv,
    private val findCvRequest: FindCvRequest,
    private val answerQuestions: AnswerQuestions,
    private val streams: ProgressStreams,
) {
    @Operation(summary = "Ask for a CV", description = "409 until you have saved a profile. Follow it at its Location.")
    @PostMapping
    fun request(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<CvRequestResponse> {
        val request = requestCv.request(jwt.ownerId())
        val location =
            ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/cv-requests/{id}")
                .buildAndExpand(request.id)
                .toUri()
        return ResponseEntity.accepted().location(location).body(CvRequestResponse.of(request))
    }

    @Operation(summary = "A CV request as it stands", description = "For a client that cannot stream, or reconnects.")
    @GetMapping("/{id}")
    fun find(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: Long,
    ): CvRequestResponse = CvRequestResponse.of(findCvRequest.find(jwt.ownerId(), id))

    @Operation(summary = "Answer the interviewer", description = "Any question left out is skipped. The writing then begins.")
    @PutMapping("/{id}/answers")
    fun answer(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: Long,
        @Valid @RequestBody answers: AnswersRequest,
    ): ResponseEntity<CvRequestResponse> =
        ResponseEntity.accepted().body(CvRequestResponse.of(answerQuestions.answer(jwt.ownerId(), id, answers.toDomain())))

    @Operation(
        summary = "Follow a CV request",
        description = "Server sent events, each named progress, each the whole request. Ends once it is completed or failed.",
    )
    @GetMapping("/{id}/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun events(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: Long,
    ): SseEmitter = streams.open(findCvRequest.find(jwt.ownerId(), id))

    @ExceptionHandler(ProfileNotFound::class)
    fun needsProfile(): ProblemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Save your profile before asking for a CV.")

    @ExceptionHandler(IllegalStateException::class)
    fun notWaiting(): ProblemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "This CV request is not waiting for answers.")

    @ExceptionHandler(IllegalArgumentException::class)
    fun badAnswers(refused: IllegalArgumentException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, refused.message ?: "Those answers cannot be taken.")
}
