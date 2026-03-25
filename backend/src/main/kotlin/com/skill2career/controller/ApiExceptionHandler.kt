package com.skill2career.controller

import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ApiErrorResponse> {
        val errors = ex.bindingResult.fieldErrors
            .map { ValidationError(it.field, it.defaultMessage ?: "Invalid value") }
            .ifEmpty { listOf(ValidationError("request", "Invalid request payload")) }

        return ResponseEntity.badRequest().body(
            ApiErrorResponse(
                error = "Validation failed",
                message = "One or more fields are invalid",
                details = errors
            )
        )
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException): ResponseEntity<ApiErrorResponse> {
        val errors = ex.constraintViolations.map {
            ValidationError(it.propertyPath.toString(), it.message)
        }

        return ResponseEntity.badRequest().body(
            ApiErrorResponse(
                error = "Validation failed",
                message = "Request parameter validation failed",
                details = errors
            )
        )
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.badRequest().body(
            ApiErrorResponse(
                error = "Validation failed",
                message = "Invalid value for '${ex.name}'",
                details = listOf(ValidationError(ex.name, "Expected a ${ex.requiredType?.simpleName ?: "valid"} value"))
            )
        )

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiErrorResponse(
                error = "Resource not found",
                message = ex.message ?: "Resource not found"
            )
        )
}

data class ApiErrorResponse(
    val error: String,
    val message: String,
    val details: List<ValidationError> = emptyList()
)

data class ValidationError(
    val field: String,
    val message: String
)
