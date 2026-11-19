package com.resourcetracker.budget

import com.resourcetracker.common.ErrorResponse
import com.resourcetracker.common.ResourceNotFoundException
import com.resourcetracker.request.entity.BudgetOverrideStatus
import com.resourcetracker.request.service.BudgetOverrideDecision
import com.resourcetracker.request.service.BudgetOverrideService
import com.resourcetracker.security.AuthenticatedUser
import com.resourcetracker.security.RequiresRoles
import com.resourcetracker.user.Role
import com.resourcetracker.user.UserRepository
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.Validator
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.LocalDate

data class CreateBudgetRequestDto(
    val departmentId: Int,
    val periodType: BudgetPeriodType,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val amount: BigDecimal,
    val active: Boolean = true,
)

data class DecideBudgetOverrideRequest(
    @field:Schema(
        description = "ID of the pending budget override to decide",
        requiredMode = RequiredMode.REQUIRED,
        example = "12",
    )
    @field:Min(1)
    val overrideId: Int,
    @field:Schema(
        description = "Managing Director decision. APPROVED commits the extra budget and authorizes the purchase. REJECTED blocks procurement for that line.",
        allowableValues = ["APPROVED", "REJECTED"],
        example = "APPROVED",
    )
    val decision: BudgetOverrideStatus,
    @field:NotBlank @field:Size(min = 10, max = 500)
    val comment: String,
)

data class UpdateBudgetRequestDto(
    val amount: BigDecimal? = null,
    val active: Boolean? = null,
)

data class BudgetResponseDto(
    val id: Long,
    val departmentId: Int,
    val periodType: BudgetPeriodType,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val amount: BigDecimal,
    val committedAmount: BigDecimal,
    val remaining: BigDecimal,
    val active: Boolean,
)

@RestController
@Validated
@RequestMapping("/api/v1/budgets")
@RequiresRoles(Role.MANAGING_DIRECTOR, Role.DEPARTMENT_MANAGER)
@Tag(name = "Budgets", description = "Department and period budget management")
@SecurityRequirement(name = "bearerAuth")
class BudgetController(
    private val budgetService: BudgetService,
    private val budgetRepository: BudgetRepository,
    private val users: UserRepository,
    private val budgetOverrides: BudgetOverrideService,
    private val objectMapper: ObjectMapper,
    private val validator: Validator,
) {
    @PostMapping
    @RequiresRoles(Role.MANAGING_DIRECTOR)
    @Operation(
        summary = "Create a budget or decide an override",
        description = "Choose a request body example. Create a department budget for a selected MONTHLY, QUARTERLY, or ANNUAL period, or decide a pending over-budget procurement override. New budgets are active by default unless active is false. Only Managing Directors can use this endpoint.",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(oneOf = [CreateBudgetRequestDto::class, DecideBudgetOverrideRequest::class]),
                    examples = [
                        ExampleObject(
                            name = "CREATE_BUDGET",
                            summary = "Create a department budget",
                            value = """
                                {
                                  "departmentId": 3,
                                  "periodType": "MONTHLY",
                                  "periodStart": "2026-11-01",
                                  "periodEnd": "2026-11-30",
                                  "amount": 2500000,
                                  "active": true
                                }
                            """,
                        ),
                        ExampleObject(
                            name = "DECIDE_OVERRIDE",
                            summary = "Approve or reject a pending budget override",
                            value = """
                                {
                                  "overrideId": 12,
                                  "decision": "APPROVED",
                                  "comment": "Approved due to urgent project requirements."
                                }
                            """,
                        ),
                    ],
                ),
            ],
        ),
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Budget override decision recorded", content = [Content(schema = Schema(implementation = com.resourcetracker.request.service.BudgetOverrideResponse::class))]),
        ApiResponse(responseCode = "201", description = "Budget created", content = [Content(schema = Schema(implementation = BudgetResponseDto::class))]),
        ApiResponse(responseCode = "400", description = "Invalid request or overlapping active budget", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Managing director role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun createOrDecide(@RequestBody body: JsonNode, @AuthenticationPrincipal caller: AuthenticatedUser): ResponseEntity<Any> {
        if (body.has("overrideId")) {
            val request = objectMapper.treeToValue(body, DecideBudgetOverrideRequest::class.java)
            validator.validate(request).takeIf { it.isNotEmpty() }?.let { throw jakarta.validation.ConstraintViolationException(it) }
            return ResponseEntity.ok(
                budgetOverrides.decide(
                    request.overrideId,
                    caller.id,
                    BudgetOverrideDecision(request.decision, request.comment),
                ),
            )
        }
        val request = objectMapper.treeToValue(body, CreateBudgetRequestDto::class.java)
        val created = budgetService.create(
            CreateBudgetRequest(
                departmentId = request.departmentId,
                periodType = request.periodType,
                periodStart = request.periodStart,
                periodEnd = request.periodEnd,
                amount = request.amount,
                active = request.active,
            )
        ).toDto()
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }

    @GetMapping("/overrides/pending")
    @RequiresRoles(Role.MANAGING_DIRECTOR)
    @Operation(summary = "List pending budget overrides", description = "Lists external procurement costs awaiting a Managing Director's over-budget decision.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Pending overrides"),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Managing Director role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    )
    fun pendingOverrides() = budgetOverrides.pending()

    @GetMapping("/{id}")
    @RequiresRoles(Role.MANAGING_DIRECTOR, Role.DEPARTMENT_MANAGER)
    @Operation(summary = "Get a budget", description = "Returns a budget by ID, including committed and remaining amounts. Department managers can only retrieve budgets belonging to their own department; Managing Directors can retrieve any budget.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Budget returned", content = [Content(schema = Schema(implementation = BudgetResponseDto::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Managing director or department manager role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Budget not found or outside the caller's department", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun get(@PathVariable @Min(1) id: Long, @AuthenticationPrincipal caller: AuthenticatedUser): BudgetResponseDto {
        val budget = budgetRepository.findById(id).orElseThrow { ResourceNotFoundException("Budget not found") }
        requireBudgetVisibility(budget.department.id, caller)
        return budget.toDto()
    }

    @GetMapping
    @RequiresRoles(Role.MANAGING_DIRECTOR, Role.DEPARTMENT_MANAGER)
    @Operation(summary = "List budgets", description = "Returns active and inactive budgets. Department managers are always limited to their own department; Managing Directors may list all departments or filter by department ID.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Budgets returned", content = [Content(schema = Schema(implementation = BudgetResponseDto::class))]),
        ApiResponse(responseCode = "400", description = "Department ID must be positive", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Managing director or department manager role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun list(
        @io.swagger.v3.oas.annotations.Parameter(description = "Department filter. Department managers may only provide their own department ID.")
        @RequestParam(required = false) @Min(1) departmentId: Int?,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ): List<BudgetResponseDto> {
        val effectiveDepartmentId = when (caller.role) {
            Role.MANAGING_DIRECTOR -> departmentId
            Role.DEPARTMENT_MANAGER -> {
                val ownDepartmentId = managerDepartmentId(caller)
                if (departmentId != null && departmentId != ownDepartmentId) {
                    throw ResponseStatusException(HttpStatus.FORBIDDEN, "Department access is not permitted")
                }
                ownDepartmentId
            }
            else -> throw ResponseStatusException(HttpStatus.FORBIDDEN, "Role is not permitted to view budgets")
        }
        return if (effectiveDepartmentId == null)
            budgetRepository.findAll().map { it.toDto() }
        else
            budgetRepository.findAllByDepartment_Id(effectiveDepartmentId).map { it.toDto() }
    }

    @PutMapping("/{id}")
    @RequiresRoles(Role.MANAGING_DIRECTOR)
    @Operation(
        summary = "Update or deactivate a budget",
        description = "Update the amount and/or active state. All fields are optional; omitted fields remain unchanged. Set `active` to false to deactivate the budget or true to reactivate it. Deactivation is rejected when the budget has active commitments.",
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Budget updated", content = [Content(schema = Schema(implementation = BudgetResponseDto::class))]),
        ApiResponse(responseCode = "400", description = "Invalid request body", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Managing director role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Budget not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "Budget has active commitments and cannot be deactivated", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun update(@PathVariable id: Long, @Valid @RequestBody request: UpdateBudgetRequestDto): BudgetResponseDto =
        budgetService.update(id, UpdateBudgetRequest(request.amount, request.active)).toDto()

    @GetMapping("/{id}/remaining")
    @RequiresRoles(Role.MANAGING_DIRECTOR, Role.DEPARTMENT_MANAGER)
    @Operation(summary = "Get remaining budget", description = "Returns the remaining amount for the active budget on the requested date. Department managers can only check budgets belonging to their own department; Managing Directors can check any budget.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Remaining amount returned"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Managing director or department manager role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Budget not found or outside the caller's department", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun remaining(
        @PathVariable @Min(1) id: Long,
        @RequestParam date: LocalDate,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ): BigDecimal {
        val budget = budgetRepository.findById(id).orElseThrow { ResourceNotFoundException("Budget not found") }
        requireBudgetVisibility(budget.department.id, caller)
        return budgetService.remaining(budget.department, date)
    }

    private fun managerDepartmentId(caller: AuthenticatedUser): Int = users.findById(caller.id)
        .orElseThrow { ResourceNotFoundException("User not found") }
        .department?.id
        ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Department access is not configured")

    private fun requireBudgetVisibility(departmentId: Int?, caller: AuthenticatedUser) {
        if (caller.role == Role.DEPARTMENT_MANAGER && departmentId != managerDepartmentId(caller)) {
            throw ResourceNotFoundException("Budget not found")
        }
    }

    private fun Budget.toDto() = BudgetResponseDto(
        id = id,
        departmentId = department.id ?: 0,
        periodType = periodType,
        periodStart = periodStart,
        periodEnd = periodEnd,
        amount = amount,
        committedAmount = committedAmount,
        remaining = amount - committedAmount,
        active = active,
    )
}
