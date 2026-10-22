package com.resourcetracker.common

import com.fasterxml.jackson.annotation.JsonInclude
import com.resourcetracker.user.Role
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.ErrorResponse as SpringErrorResponse
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

open class ApiException(
    val status: HttpStatus,
    val code: String,
    message: String?,
    val details: List<String> = emptyList(),
) : RuntimeException(message)

open class ValidationException(message: String, details: List<String> = emptyList()) :
    ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, details)

open class NotFoundException(message: String) :
    ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message)

open class ConflictException(message: String, code: String = "CONFLICT") :
    ApiException(HttpStatus.CONFLICT, code, message)

class InvalidTransitionException(message: String) :
    ConflictException(message, "INVALID_TRANSITION")

class InsufficientStockException(message: String = "Insufficient stock") :
    ConflictException(message, "INSUFFICIENT_STOCK")

class BudgetExceededException(message: String = "Budget exceeded") :
    ConflictException(message, "BUDGET_EXCEEDED")

class InvalidCredentialsException :
    ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password")

class AccountLockedException(val until: Instant? = null) :
    ApiException(
        HttpStatus.LOCKED,
        "ACCOUNT_LOCKED",
        "Account is temporarily locked. Try again later",
        listOfNotNull(until?.let { "Retry after $it" }),
    )

class AccountDeactivatedException :
    ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DEACTIVATED", "This account has been deactivated")

class InvalidRefreshTokenException :
    ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token is invalid, expired, or already used")

class UnauthenticatedException :
    ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication required")

class ForbiddenRoleException(val required: List<Role>, val actual: Role) :
    ApiException(
        HttpStatus.FORBIDDEN,
        "INSUFFICIENT_ROLE",
        "Access requires ${required.joinToString(" or ") { it.name }}; caller role is ${actual.name}",
        listOf(
            "Required role: ${required.joinToString(" or ") { it.name }}",
            "Caller role: ${actual.name}",
        ),
    )

class WeakPasswordException(val violations: List<String>) :
    ApiException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD", "Password does not meet the password policy", violations)

class DuplicateResourceException(message: String = "Resource already exists") :
    ConflictException(message, "DUPLICATE_RESOURCE")

class ResourceNotFoundException(message: String = "Resource not found") :
    NotFoundException(message)

class BusinessRuleException(message: String) :
    ConflictException(message, "BUSINESS_RULE_VIOLATION")

@JsonInclude(JsonInclude.Include.ALWAYS)
data class ErrorResponse(
    val timestamp: Instant,
    val status: Int,
    val error: String,
    val code: String,
    val message: String,
    val path: String,
    val correlationId: String,
    val details: List<String>,
) {
    companion object {
        fun from(
            request: HttpServletRequest,
            status: HttpStatusCode,
            code: String,
            message: String,
            details: List<String> = emptyList(),
        ): ErrorResponse {
            val statusValue = status.value()
            val reason = HttpStatus.resolve(statusValue)?.reasonPhrase ?: "Error"
            val correlationId = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE) as? String
                ?: request.getHeader(CorrelationIdFilter.HEADER_NAME)
                ?: "unknown"
            return ErrorResponse(
                timestamp = Instant.now(),
                status = statusValue,
                error = reason,
                code = code,
                message = message,
                path = request.requestURI,
                correlationId = correlationId,
                details = details,
            )
        }
    }
}

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun api(ex: ApiException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        response(request, ex.status, ex.code, ex.message ?: ex.code, ex.details)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(ex: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val details = ex.bindingResult.fieldErrors
            .map { "${it.field}: ${it.defaultMessage ?: "invalid value"}" }
            .sorted()
        return response(
            request,
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            "Request validation failed",
            details,
        )
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun constraintViolation(ex: ConstraintViolationException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        response(
            request,
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            "Request validation failed",
            ex.constraintViolations.map { "${it.propertyPath}: ${it.message}" }.sorted(),
        )

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun missingParameter(
        ex: MissingServletRequestParameterException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> =
        response(
            request,
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            "Request parameter '${ex.parameterName}' is required",
        )

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun typeMismatch(
        ex: MethodArgumentTypeMismatchException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> =
        response(
            request,
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            "Request parameter '${ex.name}' has an invalid value",
        )

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadable(request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        response(request, HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Malformed request body")

    @ExceptionHandler(ResponseStatusException::class)
    fun status(ex: ResponseStatusException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        response(
            request,
            ex.statusCode,
            codeForStatus(ex.statusCode.value()),
            ex.reason ?: HttpStatus.resolve(ex.statusCode.value())?.reasonPhrase ?: "Request failed",
        )

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun integrity(request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        response(
            request,
            HttpStatus.CONFLICT,
            "DATA_CONFLICT",
            "Request conflicts with existing data",
        )

    @ExceptionHandler(IllegalArgumentException::class)
    fun illegalArgument(ex: IllegalArgumentException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        response(
            request,
            HttpStatus.BAD_REQUEST,
            "INVALID_REQUEST",
            ex.message ?: "Invalid request",
        )

    @ExceptionHandler(Exception::class)
    fun unexpected(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        if (ex is SpringErrorResponse) {
            return response(
                request,
                ex.statusCode,
                codeForStatus(ex.statusCode.value()),
                ex.body.detail ?: ex.body.title ?: "Request failed",
            )
        }
        log.error("Unhandled request failure for {}", request.requestURI, ex)
        return response(
            request,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_ERROR",
            "An unexpected error occurred",
        )
    }

    private fun response(
        request: HttpServletRequest,
        status: HttpStatusCode,
        code: String,
        message: String,
        details: List<String> = emptyList(),
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(status).body(ErrorResponse.from(request, status, code, message, details))

    private fun codeForStatus(status: Int): String = when (status) {
        400 -> "VALIDATION_ERROR"
        401 -> "UNAUTHENTICATED"
        403 -> "FORBIDDEN"
        404 -> "NOT_FOUND"
        409 -> "CONFLICT"
        423 -> "ACCOUNT_LOCKED"
        else -> "HTTP_$status"
    }
}
