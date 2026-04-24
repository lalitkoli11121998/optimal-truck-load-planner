package com.smartload.optimizer.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

data class ErrorResponse(val detail: String)

@RestControllerAdvice
class GlobalExceptionHandler {

    /** Bean-validation failures (@NotBlank, @Positive, @Valid on nested objects). */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleBeanValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = ex.bindingResult.fieldErrors
            .joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
            .ifEmpty { ex.message ?: "Validation failed" }
        return ResponseEntity.badRequest().body(ErrorResponse(message))
    }

    /** Malformed JSON or missing required fields (non-nullable Kotlin types). */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleNotReadable(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.badRequest()
            .body(ErrorResponse("Malformed or missing request body: ${ex.mostSpecificCause.message}"))
    }

    /** Business-logic validation (duplicate IDs, delivery before pickup, etc.). */
    @ExceptionHandler(ValidationException::class)
    fun handleValidation(ex: ValidationException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.badRequest().body(ErrorResponse(ex.message ?: "Validation error"))
    }
}
