package com.resourcetracker.resource

import com.resourcetracker.common.ErrorResponse
import com.resourcetracker.common.PagedResponse
import com.resourcetracker.common.ListQuery
import com.resourcetracker.security.AuthenticatedUser
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
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@Validated
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports", description = "Resource, request, stock, and budget reports")
@SecurityRequirement(name = "bearerAuth")
class ReportsController(
    private val reports: InventoryReportingService,
    private val budgetRepository: com.resourcetracker.budget.BudgetRepository,
) {
    @GetMapping("/resources/utilisation")
    @RequiresRoles(Role.ADMIN, Role.MANAGING_DIRECTOR, Role.DEPARTMENT_MANAGER)
    fun utilisation(
        @RequestParam(required = false) @Min(1) departmentId: Int?,
        @RequestParam(required = false) category: ResourceCategory?,
        @RequestParam(required = false) status: ResourceStatus?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) size: Int,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ): PagedResponse<ResourceUtilisationReport> = PagedResponse.from(reports.utilisation(
        caller, departmentId, category, status, pageable(page, size),
    ))

    @GetMapping("/resources/availability")
    @RequiresRoles(Role.ADMIN, Role.MANAGING_DIRECTOR, Role.DEPARTMENT_MANAGER)
    fun availability(
        @RequestParam(required = false) @Min(1) departmentId: Int?,
        @RequestParam(required = false) category: ResourceCategory?,
        @RequestParam(required = false) status: ResourceStatus?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) size: Int,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ): PagedResponse<ResourceAvailabilityReport> = PagedResponse.from(reports.availabilityReport(
        caller, departmentId, category, status, pageable(page, size),
    ))

    @GetMapping("/resources/stock-balance")
    @RequiresRoles(Role.ADMIN, Role.MANAGING_DIRECTOR, Role.DEPARTMENT_MANAGER)
    fun stockBalance(
        @RequestParam(required = false) @Min(1) departmentId: Int?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) size: Int,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ): PagedResponse<ResourceStockBalanceReport> = PagedResponse.from(
        reports.stockBalanceReport(caller, departmentId, pageable(page, size)),
    )

    @GetMapping("/resources/waitlist")
    @RequiresRoles(Role.ADMIN, Role.MANAGING_DIRECTOR, Role.DEPARTMENT_MANAGER)
    fun waitlist(
        @RequestParam(required = false) @Min(1) departmentId: Int?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) size: Int,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ): PagedResponse<ResourceWaitlistReport> = PagedResponse.from(reports.waitlist(caller, departmentId, pageable(page, size)))

    @PostMapping("/resources/{id}/reconciliations")
    @RequiresRoles(Role.ADMIN)
    @ResponseStatus(HttpStatus.CREATED)
    fun reconcile(
        @PathVariable @Min(1) id: Int,
        @Valid @RequestBody request: ReconciliationRequest,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ): ReconciliationReport = reports.reconcile(id, caller.id, caller.role, request)

    @GetMapping("/resources/{id}/reconciliations")
    @RequiresRoles(Role.ADMIN)
    fun reconciliationHistory(
        @PathVariable @Min(1) id: Int,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ): List<ReconciliationReport> = reports.reconciliationHistory(id, caller.role)

    @GetMapping("/budgets/spending")
    @RequiresRoles(Role.ADMIN, Role.MANAGING_DIRECTOR, Role.DEPARTMENT_MANAGER)
    fun budgetSpending(
        @RequestParam(required = false) @Min(1) departmentId: Int?,
    ): List<com.resourcetracker.budget.BudgetResponseDto> =
        budgetRepository.findAll().filter { departmentId == null || it.department.id == departmentId }
            .map { b -> com.resourcetracker.budget.BudgetResponseDto(b.id, b.department.id ?: 0, b.periodType, b.periodStart, b.periodEnd, b.amount, b.committedAmount, b.amount - b.committedAmount, b.active) }

    private fun pageable(page: Int, size: Int) = ListQuery.pageable(
        page,
        size,
        "name",
        "asc",
        setOf("id", "code", "name", "category", "status", "quantity"),
        "name",
    )
}
