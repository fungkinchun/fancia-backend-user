package com.fancia.backend.user.core.controller

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ValidationHandler {
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handle(ex: MethodArgumentNotValidException): ResponseEntity<Map<String, Any>> {
        val errors = ex.bindingResult.allErrors.map { it.defaultMessage ?: "error" }

        return ResponseEntity(
            mapOf(
                "status" to 400,
                "errors" to errors
            ),
            HttpStatus.BAD_REQUEST
        )
    }
}