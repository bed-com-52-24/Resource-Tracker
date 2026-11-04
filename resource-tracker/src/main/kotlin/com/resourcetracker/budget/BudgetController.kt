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
    fun get(@PathVariable id: Long): BudgetResponseDto =
        budgetRepository.findById(id).orElseThrow { IllegalArgumentException("Budget not found") }.toDto()

    @GetMapping
    @RequiresRoles(Role.MANAGING_DIRECTOR, Role.DEPARTMENT_MANAGER)
    fun list(@RequestParam(required = false) departmentId: Int?): List<BudgetResponseDto> =
        if (departmentId == null)
            budgetRepository.findAll().map { it.toDto() }
        else
            budgetRepository.findAllByDepartment_Id(departmentId).map { it.toDto() }

    @PutMapping("/{id}")
    @RequiresRoles(Role.MANAGING_DIRECTOR)
    fun update(@PathVariable id: Long, @Valid @RequestBody request: UpdateBudgetRequestDto): BudgetResponseDto =
        budgetService.update(id, UpdateBudgetRequest(request.amount, request.active)).toDto()

    @PutMapping("/{id}/deactivate")
    @RequiresRoles(Role.MANAGING_DIRECTOR)
    fun deactivate(@PathVariable id: Long): BudgetResponseDto =
        budgetService.deactivate(id).toDto()

    @GetMapping("/{id}/remaining")
    @RequiresRoles(Role.MANAGING_DIRECTOR, Role.DEPARTMENT_MANAGER)
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