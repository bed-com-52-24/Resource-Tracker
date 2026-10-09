package com.resourcetracker.common

import com.fasterxml.jackson.annotation.JsonInclude
import com.resourcetracker.user.Role
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

open class ApiException(val status : HttpStatus, val code: String, message: String?) : RuntimeException(message)

class InvalidCredentialsException :
        ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password")
class AccountLockedException(val until: Instant? = null) :
        ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED", "Account is temporarily locked. Try again later")
class AccountDeactivatedException :
        ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DEACTIVATED", "This account has been deactivated" )
class InvalidRefreshTokenException :
        ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token is invalid refresh token, expired or already used")
class UnauthenticatedException :
        ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication required")
class ForbiddenRoleException (val required: List<Role>, val actual: Role):
        ApiException(
             HttpStatus.FORBIDDEN, "INSUFFICIENT_ROLE",
            "Requires role ${required.joinToString("or")} your role is ${actual}",
        )
class WeakPasswordException(val violations: List<String>):
        ApiException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD", "Password does not meet the password policy")

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorResponse(
    val status: Int,
    val code: String,
    val message: String,
    val requiredRoles: List<String>? = null,
    val actualRole: String? = null,
    val retryAfter: Instant? = null,
    val fieldErrors: Map<String, String>? = null,
    val details: List<String>? = null,
)

@RestControllerAdvice
class GlobalExceptionHandler{
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun api(ex: ApiException): ResponseEntity<ErrorResponse> {
        val forbidden = ex as? ForbiddenRoleException
        val body = ErrorResponse(
            status = ex.status.value(),
            code = ex.code,
            message = ex.message ?: ex.code,
            requiredRoles = forbidden?.required?.map { it.name },
            actualRole = forbidden?.actual?.name,
            retryAfter = (ex as? AccountLockedException)?.until,
            details = (ex as? WeakPasswordException)?.violations,
        )
        return ResponseEntity.status(ex.status).body(body)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val fields = ex.bindingResult.fieldErrors.associate {
            it.field to (it.defaultMessage ?: "invalid")
        }
        return ResponseEntity.badRequest().body(
            ErrorResponse(400, "VALIDATION_FAILED", "Request validation failed", fieldErrors = fields),
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadable(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        log.debug("Unreadable request body")
        return ResponseEntity.badRequest().body(
            ErrorResponse(400, "MALFORMED_REQUEST", "Malformed request body"),
        )
    }
}