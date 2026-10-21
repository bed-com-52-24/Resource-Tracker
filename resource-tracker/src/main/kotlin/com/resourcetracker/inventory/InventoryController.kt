package com.resourcetracker.inventory

import com.resourcetracker.common.ErrorResponse
import com.resourcetracker.security.RequiresRoles
import com.resourcetracker.security.AuthenticatedUser
import com.resourcetracker.user.Role
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/resources/{resourceId}/inventory")
@Tag(name = "Inventory", description = "Quantity inventory for INTERNAL resources")
@SecurityRequirement(name = "bearerAuth")
class InventoryController(private val inventory: InventoryService) {

    @PostMapping
    @RequiresRoles(Role.ADMIN, Role.DEPARTMENT_MANAGER)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create inventory at a location")
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Inventory created"),
        ApiResponse(responseCode = "400", description = "Invalid quantity or reason",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Outside department scope",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "Business rule violation",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun create(
        @PathVariable resourceId: Int,
        @Valid @RequestBody request: CreateInventoryRequest,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = inventory.create(resourceId, request, caller)

    @GetMapping
    @Operation(summary = "List inventory by location")
    fun list(@PathVariable resourceId: Int) = inventory.list(resourceId)

    @PostMapping("/transactions")
    @Operation(
        summary = "Execute an inventory command",
        description = """
            Unified endpoint for adjust / reserve / release / issue / return.

            - ADJUST  : set `totalQuantity` and `reorderLevel` for a location

            - RESERVE : hold quantity from available stock
            - RELEASE : release previously reserved quantity back to available
            - ISSUE   : consume reserved quantity (stock leaves the building)
            - RETURN  : return previously issued quantity to available stock

            `location` is optional; when omitted and the resource has exactly one location, that row is used.
        """,
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Command applied"),
        ApiResponse(responseCode = "400", description = "Invalid quantity or location required",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "Insufficient stock / business rule violation",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Inventory row not found",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun transact(
        @PathVariable resourceId: Int,
        @Valid @RequestBody request: InventoryTransactionRequest,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ): InventoryTransactionResponse = inventory.transact(resourceId, request, caller)
}
