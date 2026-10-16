package com.resourcetracker.routing_rules

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(DuplicateRoutingRuleException::class)
    fun duplicate(ex: DuplicateRoutingRuleException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            mapOf("status" to 409, "message" to ex.message)
        )

    @ExceptionHandler(NotFoundException::class)
    fun notFound(ex: NotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            mapOf("status" to 404, "message" to ex.message)
        )

    @ExceptionHandler(ValidationException::class)
    fun invalid(ex: ValidationException) =
        ResponseEntity.badRequest().body(
            mapOf("status" to 400, "message" to ex.message)
        )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalidRequest(ex: MethodArgumentNotValidException) =
        ResponseEntity.badRequest().body(
            mapOf("status" to 400, "message" to "Request validation failed")
        )

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun integrity(ex: DataIntegrityViolationException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            mapOf("status" to 409, "message" to "Database constraint violation")
        )
}