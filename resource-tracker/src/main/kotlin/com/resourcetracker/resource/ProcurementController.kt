package com.resourcetracker.resource

import com.resourcetracker.common.ErrorResponse
import com.resourcetracker.common.PagedResponse
import com.resourcetracker.common.ListQuery
import com.resourcetracker.request.service.ProcurementService
import com.resourcetracker.request.service.UpdateProcurementRequest
import com.resourcetracker.security.AuthenticatedUser
import com.resourcetracker.security.RequiresRoles
import com.resourcetracker.user.Role
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
import org.springframework.data.domain.Page
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@Validated
@RequestMapping("/api/v1/resources/procurements")
@RequiresRoles(Role.DEPARTMENT_MANAGER)
@Tag(name = "Procurements", description = "Track and update external procurement orders")
@SecurityRequirement(name = "bearerAuth")
class ProcurementController(private val procurements: ProcurementService) {

    @GetMapping
    @Operation(summary = "List procurements", description = "Returns a paginated procurement list. Search matches the associated request number; managers see procurements for their assigned requests. Page size is capped at 100.")
    fun list(
        @Parameter(description = "Search associated request numbers", example = "REQ-2026")
        @RequestParam(required = false) search: String?,
        @Parameter(description = "Zero-based page number", example = "0")
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @Parameter(description = "Page size, from 1 to 100", example = "20")
        @RequestParam(defaultValue = "20") @Min(1) size: Int,
        @Parameter(description = "Sort field: id, requestId, lineItemId, vendorId, estimatedCost, expectedDelivery, status, or receivedQuantity", example = "id")
        @RequestParam(defaultValue = "id") sort: String,
        @Parameter(description = "Sort direction: asc or desc", example = "desc")
        @RequestParam(defaultValue = "desc") direction: String,
        @Parameter(description = "Filter by procurement status", example = "ORDERED")
        @RequestParam(required = false) status: com.resourcetracker.request.entity.ProcurementStatus?,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ): PagedResponse<com.resourcetracker.request.service.ProcurementResponse> = PagedResponse.from(procurements.list(caller.id, status, search, page, size, sort, direction))

    @PutMapping("/{id}")
    @Operation(summary = "Update a procurement")
    @ApiResponses(value = [
        ApiResponse(responseCode = "409", description = "Invalid procurement transition", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun updateProcurement(
        @PathVariable @Min(1) id: Int,
        @Valid @RequestBody request: UpdateProcurementRequest,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = procurements.update(id, caller.id, request)
}