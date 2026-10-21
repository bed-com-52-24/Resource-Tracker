package com.resourcetracker.request.controller

import com.resourcetracker.request.entity.ProcurementStatus
import com.resourcetracker.request.service.*
import com.resourcetracker.security.AuthenticatedUser
import com.resourcetracker.security.RequiresRoles
import com.resourcetracker.user.Role
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@Validated
class StockOperationsController(
    private val stock: StockOperationsService,
    private val procurements: ProcurementService,
) {
    @PostMapping("/api/v1/requests/{id}/issues")
    @RequiresRoles(Role.DEPARTMENT_MANAGER)
    @ResponseStatus(HttpStatus.CREATED)
    fun issue(
        @PathVariable @Min(1) id: Int,
        @Valid @RequestBody request: StockIssueRequest,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = stock.issue(id, caller.id, request)

    @PostMapping("/api/v1/issues/{issueId}/returns")
    @RequiresRoles(Role.TEAM_LEAD, Role.DEPARTMENT_MANAGER)
    @ResponseStatus(HttpStatus.CREATED)
    fun returnStock(
        @PathVariable @Min(1) issueId: Int,
        @Valid @RequestBody request: CreateReturnRequest,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = stock.requestReturn(issueId, caller.id, request)

    @PutMapping("/api/v1/returns/{returnId}")
    @RequiresRoles(Role.DEPARTMENT_MANAGER)
    fun verifyReturn(
        @PathVariable @Min(1) returnId: Int,
        @Valid @RequestBody request: VerifyReturnRequest,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = stock.verifyReturn(returnId, caller.id, request)

    @PostMapping("/api/v1/resources/{id}/corrections")
    @RequiresRoles(Role.ADMIN)
    fun correct(
        @PathVariable @Min(1) id: Int,
        @Valid @RequestBody request: CorrectionRequestDto,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = stock.correctResource(id, caller.id, StockCorrectionRequest(request.newQuantity, request.reason))

    @GetMapping("/api/v1/procurements")
    @RequiresRoles(Role.ADMIN, Role.DEPARTMENT_MANAGER)
    fun procurements(
        @RequestParam(required = false) status: ProcurementStatus?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) size: Int,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = procurements.list(caller.id, status, page, size)

    @PutMapping("/api/v1/procurements/{id}")
    @RequiresRoles(Role.DEPARTMENT_MANAGER)
    fun updateProcurement(
        @PathVariable @Min(1) id: Int,
        @Valid @RequestBody request: UpdateProcurementRequest,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = procurements.update(id, caller.id, request)
}

data class CorrectionRequestDto(
    @field:PositiveOrZero val newQuantity: Int,
    @field:NotBlank val reason: String,
)
