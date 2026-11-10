package com.resourcetracker.resource

import com.resourcetracker.common.ErrorResponse
import com.resourcetracker.common.PagedResponse
import com.resourcetracker.common.ListQuery
import com.resourcetracker.security.AuthenticatedUser
import com.resourcetracker.security.RequiresRoles
import com.resourcetracker.user.Role
import com.resourcetracker.user.UserRepository
import com.resourcetracker.common.ResourceNotFoundException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
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
import org.springframework.web.server.ResponseStatusException

@RestController
@Validated
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports", description = "Resource, request, stock, and budget reports")
@SecurityRequirement(name = "bearerAuth")
class ReportsController(
    private val reports: InventoryReportingService,
    private val budgetRepository: com.resourcetracker.budget.BudgetRepository,
    private val users: UserRepository,
) {
    @GetMapping("/resources/utilisation")
    @RequiresRoles(Role.ADMIN, Role.MANAGING_DIRECTOR, Role.DEPARTMENT_MANAGER)
    @Operation(summary = "Resource utilisation report", description = "Returns a paginated summary of resource usage. Department managers are restricted to their department; managing directors and admins may filter by department.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Utilisation report returned"),
        ApiResponse(responseCode = "400", description = "Invalid filters or pagination", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Department access is not permitted", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun utilisation(
        @Parameter(description = "Department filter; managers can only access their own department") @RequestParam(required = false) @Min(1) departmentId: Int?,
        @Parameter(description = "Optional resource category filter") @RequestParam(required = false) category: ResourceCategory?,
        @Parameter(description = "Optional inventory status filter") @RequestParam(required = false) status: ResourceStatus?,
        @Parameter(description = "Zero-based page number") @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "20") @Min(1) size: Int,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ): PagedResponse<ResourceUtilisationReport> = PagedResponse.from(reports.utilisation(
        caller, departmentId, category, status, pageable(page, size),
    ))

    @GetMapping("/resources/availability")
    @RequiresRoles(Role.ADMIN, Role.MANAGING_DIRECTOR, Role.DEPARTMENT_MANAGER)
    @Operation(summary = "Resource availability report", description = "Returns a paginated summary of on-hand, reserved, unavailable, and available stock. Department managers are restricted to their department.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Availability report returned"),
        ApiResponse(responseCode = "400", description = "Invalid filters or pagination", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Department access is not permitted", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
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
    @Operation(summary = "Resource stock balance report", description = "Returns paginated stock balances, including on-hand, reserved, unavailable, and available quantities. Department managers are restricted to their department.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Stock balance report returned"),
        ApiResponse(responseCode = "400", description = "Invalid filters or pagination", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Department access is not permitted", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
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
    @Operation(summary = "Resource waitlist report", description = "Returns paginated requests waiting for resource stock. Department managers are restricted to their department.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Waitlist report returned"),
        ApiResponse(responseCode = "400", description = "Invalid filters or pagination", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Department access is not permitted", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun waitlist(
        @RequestParam(required = false) @Min(1) departmentId: Int?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) size: Int,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ): PagedResponse<ResourceWaitlistReport> = PagedResponse.from(
        reports.waitlist(caller, departmentId, PageRequest.of(page, size)),
    )

    @PostMapping("/resources/{id}/reconciliations")
    @RequiresRoles(Role.MANAGING_DIRECTOR)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Reconcile resource stock", description = "Managing directors reconcile recorded stock with a physical count and provide a reason. Creates an auditable reconciliation record.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Stock reconciliation created"),
        ApiResponse(responseCode = "400", description = "Invalid physical count or reason", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Managing Director role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Resource not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "The reconciliation conflicts with current stock constraints", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun reconcile(
        @Parameter(description = "Resource ID", example = "12") @PathVariable @Min(1) id: Int,
        @Valid @RequestBody request: ReconciliationRequest,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ): ReconciliationReport = reports.reconcile(id, caller.id, caller.role, request)

    @GetMapping("/resources/{id}/reconciliations")
    @RequiresRoles(Role.MANAGING_DIRECTOR)
    @Operation(summary = "List resource reconciliations", description = "Returns the reconciliation history for a resource. Managing director access is required.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Reconciliation history returned"),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Managing Director role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Resource not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun reconciliationHistory(
        @Parameter(description = "Resource ID", example = "12") @PathVariable @Min(1) id: Int,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ): List<ReconciliationReport> = reports.reconciliationHistory(id, caller.role)

    @GetMapping("/budgets/spending")
    @RequiresRoles(Role.ADMIN, Role.MANAGING_DIRECTOR, Role.DEPARTMENT_MANAGER)
    @Operation(summary = "List budget spending", description = "Returns budgets with committed and remaining amounts, optionally filtered by department. Department managers can only access their own department.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Budget spending returned"),
        ApiResponse(responseCode = "400", description = "Invalid department filter", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Department access is not permitted", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun budgetSpending(
        @Parameter(description = "Optional department filter") @RequestParam(required = false) @Min(1) departmentId: Int?,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ): List<com.resourcetracker.budget.BudgetResponseDto> {
        val departmentScope = if (caller.role == Role.DEPARTMENT_MANAGER) {
            users.findById(caller.id).orElseThrow { ResourceNotFoundException("User not found") }.department?.id
                ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Department access is not configured")
        } else {
            departmentId
        }
        if (caller.role == Role.DEPARTMENT_MANAGER && departmentId != null && departmentId != departmentScope) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Department access is not permitted")
        }
        return budgetRepository.findAll().filter { departmentScope == null || it.department.id == departmentScope }
            .map { b -> com.resourcetracker.budget.BudgetResponseDto(b.id, b.department.id ?: 0, b.periodType, b.periodStart, b.periodEnd, b.amount, b.committedAmount, b.amount - b.committedAmount, b.active) }
    }

    private fun pageable(page: Int, size: Int) = ListQuery.pageable(
        page,
        size,
        "name",
        "asc",
        setOf("id", "code", "name", "category", "status", "quantity"),
        "name",
    )
}
