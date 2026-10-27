package com.resourcetracker.common

import com.fasterxml.jackson.annotation.JsonInclude
import com.resourcetracker.user.Role
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

open class ApiException(
    val status: HttpStatus,
    val code: String,
    message: String
) : RuntimeException(message)

class ValidationException(message: String) :
    ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message)

open class NotFoundException(message: String = "Resource not found") :
    ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message)

open class ConflictException(message: String = "Resource conflict") :
    ApiException(HttpStatus.CONFLICT, "RESOURCE_CONFLICT", message)

class BadRequestException(message: String) :
    ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message)

class InvalidTransitionException(message: String) :
    ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION", message)

class InsufficientStockException(message: String = "Insufficient stock") :
    ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", message)

class BudgetExceededException(message: String = "Budget exceeded") :
    ApiException(HttpStatus.CONFLICT, "BUDGET_EXCEEDED", message)

class InvalidCredentialsException :
    ApiException(
        HttpStatus.UNAUTHORIZED,
        "INVALID_CREDENTIALS",
        "Invalid username or password"
    )

class AccountLockedException(val until: Instant? = null) :
    ApiException(
        HttpStatus.LOCKED,
        "ACCOUNT_LOCKED",
        "Account is temporarily locked. Try again later"
    )

class AccountDeactivatedException :
    ApiException(
        HttpStatus.FORBIDDEN,
        "ACCOUNT_DEACTIVATED",
        "This account has been deactivated"
    )

class InvalidRefreshTokenException :
    ApiException(
        HttpStatus.UNAUTHORIZED,
        "INVALID_REFRESH_TOKEN",
        "Refresh token is invalid, expired or already used"
    )

class UnauthenticatedException :
    ApiException(
        HttpStatus.UNAUTHORIZED,
        "UNAUTHENTICATED",
        "Authentication required"
    )

class ForbiddenRoleException(
    val required: List<Role>,
    val actual: Role
) : ApiException(
    HttpStatus.FORBIDDEN,
    "INSUFFICIENT_ROLE",
    "Insufficient role"
)

class WeakPasswordException(val violations: List<String>) :
    ApiException(
        HttpStatus.BAD_REQUEST,
        "WEAK_PASSWORD",
        "Password does not meet the password policy"
    )

class DuplicateResourceException(
    message: String = "Resource already exists",
) : ApiException(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", message)
class ResourceNotFoundException(message: String = "Resource not found") :
    ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message)
class BadRequestException(message: String) :
    ApiException(HttpStatus.BAD_REQUEST, "INVALID_RESOURCE_REQUEST", message)
class BusinessRuleException(message: String) :
    ApiException(
        HttpStatus.CONFLICT,
        "BUSINESS_RULE_VIOLATION",
        message
    )

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorResponse(
    val timestamp: Instant,
    val status: Int,
    val error: String,
    val code: String,
    val message: String,
    val path: String,
    val correlationId: String,
    val details: List<String> = emptyList()
)

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun api(
        ex: ApiException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        val forbidden = ex as? ForbiddenRoleException

        val details = when {
            forbidden != null -> listOf(
                "Required role: ${forbidden.required.joinToString(" or ") { it.name }}",
                "Actual role: ${forbidden.actual.name}"
            )

            ex is WeakPasswordException -> ex.violations

            ex is AccountLockedException && ex.until != null ->
                listOf("Account lock expires at: ${ex.until}")

            else -> emptyList()
        }

        return respond(
            status = ex.status,
            code = ex.code,
            message = ex.message ?: ex.code,
            details = details,
            request = request
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        val details = ex.bindingResult.fieldErrors.map {
            "${it.field}: ${it.defaultMessage ?: "Invalid value"}"
        }

        return respond(
            status = HttpStatus.BAD_REQUEST,
            code = "VALIDATION_FAILED",
            message = "Request validation failed",
            details = details,
            request = request
        )
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun constraintViolation(
        ex: ConstraintViolationException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> =
        respond(
            status = HttpStatus.BAD_REQUEST,
            code = "VALIDATION_FAILED",
            message = "Request validation failed",
            details = ex.constraintViolations.map { it.message }.sorted(),
            request = request
        )

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadable(
        ex: HttpMessageNotReadableException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.debug("Malformed request body")

        return respond(
            status = HttpStatus.BAD_REQUEST,
            code = "MALFORMED_REQUEST",
            message = "Malformed request body",
            request = request
        )
    }

    @ExceptionHandler(Exception::class)
    fun unexpected(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        val correlationId = correlationId(request)

        log.error(
            "Unexpected error. correlationId={}",
            correlationId,
            ex
        )

        return respond(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            code = "INTERNAL_SERVER_ERROR",
            message = "An unexpected error occurred",
            request = request,
            correlationId = correlationId
        )
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun status(
        ex: ResponseStatusException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> =
        respond(
            status = ex.statusCode,
            code = "REQUEST_FAILED",
            message = ex.reason ?: "Request failed",
            request = request
        )

    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun optimisticLock(
        ex: OptimisticLockingFailureException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> =
        respond(
            status = HttpStatus.CONFLICT,
            code = "CONCURRENT_MODIFICATION",
            message = "This record changed while you were acting. Reload and try again.",
            request = request
        )

    @ExceptionHandler(IllegalArgumentException::class)
    fun illegalArgument(
        ex: IllegalArgumentException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> =
        respond(
            status = HttpStatus.BAD_REQUEST,
            code = "INVALID_REQUEST",
            message = ex.message ?: "Invalid request",
            request = request
        )

    private fun respond(
        status: HttpStatusCode,
        code: String,
        message: String,
        request: HttpServletRequest,
        details: List<String> = emptyList(),
        correlationId: String = correlationId(request)
    ): ResponseEntity<ErrorResponse> {
        val body = ErrorResponse(
            timestamp = Instant.now(),
            status = status.value(),
            error = HttpStatus.resolve(status.value())?.reasonPhrase ?: "HTTP ${status.value()}",
            code = code,
            message = message,
            path = request.requestURI,
            correlationId = correlationId,
            details = details
        )

        return ResponseEntity.status(status)
            .header("X-Correlation-ID", correlationId)
            .body(body)
    }

    private fun correlationId(request: HttpServletRequest): String {
        val supplied = request.getHeader("X-Correlation-ID")

        return supplied
            ?.takeIf { it.matches(Regex("[A-Za-z0-9._-]{1,100}")) }
            ?: UUID.randomUUID().toString()
    }
}
