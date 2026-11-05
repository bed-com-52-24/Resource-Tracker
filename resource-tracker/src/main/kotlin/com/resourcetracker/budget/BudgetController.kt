package com.resourcetracker.budget

import com.resourcetracker.common.ErrorResponse
import com.resourcetracker.security.RequiresRoles
import com.resourcetracker.user.Role
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
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
) {
    @PostMapping
    @RequiresRoles(Role.MANAGING_DIRECTOR)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a budget", description = "Create a department budget for a period. The budget is active by default.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Budget created", content = [Content(schema = Schema(implementation = BudgetResponseDto::class))]),
        ApiResponse(responseCode = "400", description = "Invalid request or overlapping active budget", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Managing director role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun create(@Valid @RequestBody request: CreateBudgetRequestDto): BudgetResponseDto =
        budgetService.create(
            CreateBudgetRequest(
                departmentId = request.departmentId,
                periodType = request.periodType,
                periodStart = request.periodStart,
                periodEnd = request.periodEnd,
                amount = request.amount,
                active = request.active,
            )
        ).toDto()

    @GetMapping("/{id}")
    @RequiresRoles(Role.MANAGING_DIRECTOR, Role.DEPARTMENT_MANAGER)
    @Operation(summary = "Get a budget", description = "Return a budget by its numeric identifier, including committed and remaining amounts.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Budget returned", content = [Content(schema = Schema(implementation = BudgetResponseDto::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Managing director or department manager role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Budget not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun get(@PathVariable id: Long): BudgetResponseDto =
        budgetRepository.findById(id).orElseThrow { IllegalArgumentException("Budget not found") }.toDto()

    @GetMapping
    @RequiresRoles(Role.MANAGING_DIRECTOR, Role.DEPARTMENT_MANAGER)
    @Operation(summary = "List budgets", description = "Return all budgets, optionally filtered by department ID. Both active and inactive budgets are included.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Budgets returned", content = [Content(schema = Schema(implementation = BudgetResponseDto::class))]),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Managing director or department manager role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun list(@RequestParam(required = false) departmentId: Int?): List<BudgetResponseDto> =
        if (departmentId == null)
            budgetRepository.findAll().map { it.toDto() }
        else
            budgetRepository.findAllByDepartment_Id(departmentId).map { it.toDto() }

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
    @Operation(summary = "Get remaining budget", description = "Return the remaining amount for the active budget belonging to the budget's department on the requested date.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Remaining amount returned"),
        ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Managing director or department manager role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Budget not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun remaining(@PathVariable id: Long, @RequestParam date: LocalDate): BigDecimal {
        val budget = budgetRepository.findById(id).orElseThrow { IllegalArgumentException("Budget not found") }
        return budgetService.remaining(budget.department, date)
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