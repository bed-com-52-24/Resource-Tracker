package com.resourcetracker.common

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException
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
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.servlet.resource.NoResourceFoundException
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
    val details: List<String> = emptyList(),
    val action: String,
    val fieldErrors: List<FieldErrorResponse> = emptyList(),
)

data class FieldErrorResponse(
    val field: String,
    val message: String,
    val action: String,
)

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun api(
        ex: ApiException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        val details = when {
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
        val details = ex.bindingResult.allErrors.map { error ->
            val field = (error as? org.springframework.validation.FieldError)?.field
                ?: error.objectName
            validationDetail(field, error.defaultMessage)
        }.distinct().sorted()

        return respond(
            status = HttpStatus.BAD_REQUEST,
            code = "VALIDATION_FAILED",
            message = "Please review the information you entered and correct the fields listed below.",
            details = details,
            fieldErrors = ex.bindingResult.fieldErrors.map { error ->
                FieldErrorResponse(
                    field = error.field,
                    message = validationMessage(error.defaultMessage),
                    action = "Provide a valid value for ${fieldLabel(error.field)}.",
                )
            }.distinctBy { it.field to it.message }.sortedBy { it.field },
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
            message = "Please review the information you entered and correct the fields listed below.",
            details = ex.constraintViolations.map {
                val field = it.propertyPath.lastOrNull()?.name
                validationDetail(field, it.message)
            }.distinct().sorted(),
            fieldErrors = ex.constraintViolations.mapNotNull {
                val field = it.propertyPath.lastOrNull()?.name ?: return@mapNotNull null
                FieldErrorResponse(field, validationMessage(it.message), "Provide a valid value for ${fieldLabel(field)}.")
            }.distinctBy { it.field to it.message }.sortedBy { it.field },
            request = request
        )

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadable(
        ex: HttpMessageNotReadableException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        val details = bodyFailureDetails(ex)
        log.debug("Unreadable request body: {}", details.joinToString("; "))

        return respond(
            status = HttpStatus.BAD_REQUEST,
            code = "MALFORMED_REQUEST",
            message = "Request body is invalid",
            details = details,
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
            message = "We couldn't complete your request right now. Please try again. If the problem continues, contact support and provide the reference ID.",
            details = listOf("Reference ID: $correlationId"),
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

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun dataIntegrity(
        ex: DataIntegrityViolationException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        log.info("Data integrity rule rejected request at {}", request.requestURI)
        return respond(
            status = HttpStatus.CONFLICT,
            code = "DATA_INTEGRITY_VIOLATION",
            message = "This change conflicts with existing data or violates a data rule. Check the referenced records and required values, then try again.",
            request = request,
        )
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun missingParameter(
        ex: MissingServletRequestParameterException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> = respond(
        status = HttpStatus.BAD_REQUEST,
        code = "MISSING_PARAMETER",
        message = "Required parameter '${ex.parameterName}' is missing.",
        details = listOf("Provide '${ex.parameterName}' as a ${ex.parameterType} value."),
        request = request,
    )

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun parameterTypeMismatch(
        ex: MethodArgumentTypeMismatchException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        val type = ex.requiredType
        val expected = when {
            type == null -> "the required format"
            type.isEnum -> type.enumConstants.joinToString { (it as Enum<*>).name }
            else -> friendlyType(type)
        }
        return respond(
            status = HttpStatus.BAD_REQUEST,
            code = "INVALID_PARAMETER",
            message = "Parameter '${ex.name}' has an invalid value.",
            details = listOf("Expected $expected."),
            request = request,
        )
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun methodNotSupported(
        ex: HttpRequestMethodNotSupportedException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> = respond(
        status = HttpStatus.METHOD_NOT_ALLOWED,
        code = "METHOD_NOT_ALLOWED",
        message = "${ex.method ?: "This HTTP method"} is not supported for this endpoint.",
        details = ex.supportedHttpMethods?.map { "Use ${it.name()} instead." }.orEmpty(),
        request = request,
    )

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun mediaTypeNotSupported(
        ex: HttpMediaTypeNotSupportedException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> = respond(
        status = HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        code = "UNSUPPORTED_MEDIA_TYPE",
        message = "The request body uses an unsupported content type.",
        details = listOf("Use Content-Type: application/json."),
        request = request,
    )

    @ExceptionHandler(NoResourceFoundException::class)
    fun routeNotFound(
        ex: NoResourceFoundException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> = respond(
        status = HttpStatus.NOT_FOUND,
        code = "ENDPOINT_NOT_FOUND",
        message = "No endpoint was found for this path.",
        request = request,
    )

    @ExceptionHandler(IllegalArgumentException::class)
    fun illegalArgument(
        ex: IllegalArgumentException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> =
        respond(
            status = HttpStatus.BAD_REQUEST,
            code = "INVALID_REQUEST",
            message = "Some information in your request is invalid. Check the accepted values and formats, then try again.",
            request = request
        )

    private fun respond(
        status: HttpStatusCode,
        code: String,
        message: String,
        request: HttpServletRequest,
        details: List<String> = emptyList(),
        fieldErrors: List<FieldErrorResponse> = emptyList(),
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
            details = details,
            action = recommendedAction(status.value()),
            fieldErrors = fieldErrors,
        )

        return ResponseEntity.status(status)
            .header("X-Correlation-ID", correlationId)
            .body(body)
    }

    private fun bodyFailureDetails(ex: HttpMessageNotReadableException): List<String> {
        val causes = generateSequence(ex as Throwable?) { it.cause }.toList()
        val missing = causes.firstOrNull { it.javaClass.simpleName == "MissingKotlinParameterException" }
        if (missing != null) {
            val parameterName = runCatching {
                val parameter = missing.javaClass.getMethod("getParameter").invoke(missing)
                parameter?.javaClass?.getMethod("getName")?.invoke(parameter)?.toString()
            }.getOrNull()
            val field = (missing as? JsonMappingException)?.path
                ?.lastOrNull()?.fieldName
                ?: parameterName
                ?: (missing as? JsonMappingException)?.pathReference
            return listOf(if (field.isNullOrBlank()) "A required field is missing." else "${fieldLabel(field)} is required.")
        }

        val unknown = causes.filterIsInstance<UnrecognizedPropertyException>().firstOrNull()
        if (unknown != null) {
            val known = unknown.knownPropertyIds.orEmpty().map { it.toString() }.sorted()
            val field = requestField(unknown)
            return listOfNotNull(
                "${field?.let { "${fieldLabel(it)}: "}.orEmpty()}isn't recognized.",
                known.takeIf { it.isNotEmpty() }?.let { "Use one of these field names: ${it.joinToString(", ")}." },
            )
        }

        val invalid = causes.filterIsInstance<InvalidFormatException>().firstOrNull()
        if (invalid != null) {
            val field = requestField(invalid)
            val type = invalid.targetType
            val expectation = if (type.isEnum) {
                "Use one of: ${type.enumConstants.joinToString { (it as Enum<*>).name }}."
            } else {
                "Expected ${friendlyType(type)}."
            }
            return listOf("${field?.let { "${fieldLabel(it)}: "}.orEmpty()}has an invalid value. $expectation")
        }

        val parse = causes.filterIsInstance<JsonParseException>().firstOrNull()
        if (parse != null) {
            val location = parse.location
            return listOf("JSON syntax error${location?.let { " at line ${it.lineNr}, column ${it.columnNr}" }.orEmpty()}. Check commas, quotes, braces, and brackets.")
        }

        val mismatch = causes.filterIsInstance<MismatchedInputException>().firstOrNull()
        if (mismatch != null) {
            val field = requestField(mismatch)
            return listOf("${field?.let { "${fieldLabel(it)}: "}.orEmpty()}must be ${friendlyType(mismatch.targetType)}.")
        }

        if (ex.message.orEmpty().contains("request body is missing", ignoreCase = true)) {
            return listOf("Request body is required.")
        }
        return listOf("Send a valid JSON body with all required fields and the expected value types.")
    }

    private fun requestField(exception: JsonMappingException): String? = exception.path
        .mapNotNull { reference -> reference.fieldName ?: reference.index.takeIf { it >= 0 }?.toString() }
        .joinToString(".")
        .takeIf { it.isNotBlank() }

    private fun validationDetail(field: String?, message: String?): String {
        val label = field?.takeIf { it.isNotBlank() }?.let(::fieldLabel)
        val rule = message.orEmpty()
        val friendly = when {
            rule.contains("must not be blank", ignoreCase = true) ||
                rule.contains("must not be empty", ignoreCase = true) ||
                rule.contains("must not be null", ignoreCase = true) -> "is required."
            rule.startsWith("must be a well-formed", ignoreCase = true) -> "must be in a valid format."
            rule.startsWith("must be a valid", ignoreCase = true) -> "must be valid."
            else -> {
                val message = rule.ifBlank { "Please check this value." }
                val normalized = if (label == null) {
                    message.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                } else {
                    message.replaceFirstChar { it.lowercase() }
                }
                if (normalized.endsWith('.')) normalized else "$normalized."
            }
        }
        return if (label == null) friendly else "$label $friendly"
    }

    private fun validationMessage(message: String?): String {
        val rule = message.orEmpty()
        return when {
            rule.contains("must not be blank", ignoreCase = true) ||
                rule.contains("must not be empty", ignoreCase = true) ||
                rule.contains("must not be null", ignoreCase = true) -> "This field is required."
            rule.startsWith("must be a well-formed", ignoreCase = true) -> "Enter a value in a valid format."
            rule.startsWith("must be a valid", ignoreCase = true) -> "Enter a valid value."
            rule.isBlank() -> "Check this value."
            else -> rule.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    private fun recommendedAction(status: Int): String = when (status) {
        400, 422 -> "Review the request and correct the indicated values, then try again."
        401 -> "Sign in and try again."
        403 -> "Contact an administrator if you believe you need access to this action."
        404 -> "Check the address or selected item and try again."
        409 -> "Review the current record state, resolve the conflict, and try again."
        429 -> "Wait briefly, then try again."
        in 500..599 -> "Try again shortly. If the problem continues, contact support with the reference ID."
        else -> "Review the request and try again."
    }

    private fun fieldLabel(field: String): String = field
        .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    private fun friendlyType(type: Class<*>): String = when (type) {
        String::class.java -> "text"
        java.lang.Integer::class.java, Int::class.javaPrimitiveType -> "an integer"
        java.lang.Long::class.java, Long::class.javaPrimitiveType -> "an integer"
        java.lang.Boolean::class.java, Boolean::class.javaPrimitiveType -> "true or false"
        java.math.BigDecimal::class.java -> "a number"
        java.time.LocalDate::class.java -> "a date in YYYY-MM-DD format"
        else -> type.simpleName.ifBlank { "the expected type" }
    }

    private fun correlationId(request: HttpServletRequest): String {
        val supplied = request.getHeader("X-Correlation-ID")

        return supplied
            ?.takeIf { it.matches(Regex("[A-Za-z0-9._-]{1,100}")) }
            ?: UUID.randomUUID().toString()
    }
}
