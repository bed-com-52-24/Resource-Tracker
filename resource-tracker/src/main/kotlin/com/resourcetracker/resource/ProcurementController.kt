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
    @Operation(summary = "List procurements", description = "Department managers can list procurements for requests assigned to them. Search matches the associated request number; status is an optional filter. Page size is capped at 100.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Procurements returned"),
        ApiResponse(responseCode = "400", description = "Invalid search, status, sort, or pagination parameter", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Department manager role required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
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
        @Parameter(description = "Filter by status: AUTHORISED, ORDERED, RECEIVED, ACCEPTED, or CANCELLED.", example = "ORDERED")
        @RequestParam(required = false) status: com.resourcetracker.request.entity.ProcurementStatus?,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ): PagedResponse<com.resourcetracker.request.service.ProcurementResponse> = PagedResponse.from(procurements.list(caller.id, status, search, page, size, sort, direction))

    @PutMapping("/{id}")
    @Operation(
        summary = "Update a procurement",
        description = "Department Managers can update procurements for requests assigned to them. Send only the next status in this order: AUTHORISED, ORDERED, RECEIVED, ACCEPTED. When sending RECEIVED, include receivedQuantity from 1 through the authorized quantity. The total received across orders cannot exceed the approved line quantity. CANCELLED is set by the system and cannot be sent here.",
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Procurement updated"),
        ApiResponse(responseCode = "400", description = "Invalid request body", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Caller is not the assigned department manager", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Procurement not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "Invalid procurement transition", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun updateProcurement(
        @PathVariable @Min(1) id: Int,
        @Valid @RequestBody request: UpdateProcurementRequest,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = procurements.update(id, caller.id, request)
}
