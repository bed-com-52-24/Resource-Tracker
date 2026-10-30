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
@RequestMapping("/api/v1/resources")
@Tag(name = "Resource Inventory Reports", description = "Internal resource utilisation, availability, waitlist, and reconciliation reports")
@SecurityRequirement(name = "bearerAuth")
class InventoryReportController(private val reports: InventoryReportingService) {
    @GetMapping("/reports/utilisation")
    @RequiresRoles(Role.ADMIN, Role.MANAGING_DIRECTOR, Role.DEPARTMENT_MANAGER)
    @Operation(
        summary = "Report internal resource utilisation",
        description = "Utilisation rate is currently issued units divided by total managed units, multiplied by 100. Total managed units are on-hand + unavailable + currently issued. Overdue quantities use the request requiredDate as the available return-date proxy.",
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Utilisation report"),
        ApiResponse(responseCode = "400", description = "Invalid report filters", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Admin, Managing Director, or department-scoped Manager access required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
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

    @GetMapping("/reports/availability")
    @RequiresRoles(Role.ADMIN, Role.MANAGING_DIRECTOR, Role.DEPARTMENT_MANAGER)
    @Operation(summary = "Report internal resource availability", description = "Filters by department, category, and physical stock status. Department Managers are limited to their own department.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Availability report"),
        ApiResponse(responseCode = "400", description = "Invalid report filters", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Admin, Managing Director, or department-scoped Manager access required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
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

    @GetMapping("/reports/waitlist")
    @RequiresRoles(Role.ADMIN, Role.MANAGING_DIRECTOR, Role.DEPARTMENT_MANAGER)
    @Operation(
        summary = "Report approved requests waiting for internal stock",
        description = "Expected availability is estimated from outstanding issues' request required dates. It is null when no known future return date can cover the shortfall.",
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Waitlist report"),
        ApiResponse(responseCode = "400", description = "Invalid department filter", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Admin, Managing Director, or department-scoped Manager access required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun waitlist(
        @RequestParam(required = false) @Min(1) departmentId: Int?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) size: Int,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ): PagedResponse<ResourceWaitlistReport> = PagedResponse.from(reports.waitlist(caller, departmentId, pageable(page, size)))

    @PostMapping("/{id}/reconciliations")
    @RequiresRoles(Role.ADMIN)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Submit a physical stock reconciliation",
        description = "Records the system count, physical count, and signed variance. Set adjustStock=true to apply the count; applied adjustments create a stock correction movement.",
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Reconciliation recorded", content = [Content(schema = Schema(implementation = ReconciliationReport::class))]),
        ApiResponse(responseCode = "400", description = "Invalid count, reason, or resource type", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Admin role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Resource not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "Adjustment would violate reserved or unavailable stock constraints", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun reconcile(
        @PathVariable @Min(1) id: Int,
        @Valid @RequestBody request: ReconciliationRequest,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ): ReconciliationReport = reports.reconcile(id, caller.id, caller.role, request)

    @GetMapping("/{id}/reconciliations")
    @RequiresRoles(Role.ADMIN)
    @Operation(summary = "List resource reconciliation history", description = "Admin only. Returns variance entries from oldest to newest.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Reconciliation history"),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Admin role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Resource not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun reconciliationHistory(
        @PathVariable @Min(1) id: Int,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ): List<ReconciliationReport> = reports.reconciliationHistory(id, caller.role)

    private fun pageable(page: Int, size: Int) = ListQuery.pageable(
        page,
        size,
        "name",
        "asc",
        setOf("id", "code", "name", "category", "status", "quantity"),
        "name",
    )
}
