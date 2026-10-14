package com.resourcetracker.inventory

import com.resourcetracker.common.ErrorResponse
import com.resourcetracker.security.RequiresRoles
import com.resourcetracker.security.AuthenticatedUser
import com.resourcetracker.user.Role
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/resources/{resourceId}/inventory")
@Tag(name = "Inventory", description = "Quantity inventory for INTERNAL resources")
@SecurityRequirement(name = "bearerAuth")
class InventoryController(private val inventory: InventoryService) {

    @PostMapping
    @RequiresRoles(Role.ADMIN, Role.DEPARTMENT_MANAGER)
    @Operation(summary = "Create inventory at a location")
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Inventory created"),
        ApiResponse(responseCode = "400", description = "Invalid quantity or reason", content = [io.swagger.v3.oas.annotations.media.Content(schema = io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Outside department scope", content = [io.swagger.v3.oas.annotations.media.Content(schema = io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "Business rule violation", content = [io.swagger.v3.oas.annotations.media.Content(schema = io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse::class))]),
    ])
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    fun create(
        @PathVariable resourceId: UUID,
        @Valid @RequestBody request: CreateInventoryRequest,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = inventory.create(resourceId, request, caller)

    @PatchMapping("/{location}")
    @RequiresRoles(Role.ADMIN, Role.DEPARTMENT_MANAGER)
    @Operation(summary = "Adjust inventory quantity")
    fun adjust(
        @PathVariable resourceId: UUID,
        @PathVariable location: String,
        @Valid @RequestBody request: AdjustInventoryRequest,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = inventory.adjust(resourceId, location, request, caller)

    @GetMapping
    @Operation(summary = "List inventory by location")
    fun list(@PathVariable resourceId: UUID) = inventory.list(resourceId)

    @GetMapping("/available")
    @Operation(summary = "Get total available quantity")
    fun available(@PathVariable resourceId: UUID) = mapOf("resourceId" to resourceId, "availableQuantity" to inventory.available(resourceId))

    @PostMapping("/reserve")
    @Operation(summary = "Reserve quantity")
    fun reserve(@PathVariable resourceId: UUID, @Valid @RequestBody request: InventoryQuantityRequest) =
        inventory.reserve(resourceId, request.quantity)

    @PostMapping("/release")
    @Operation(summary = "Release a reservation")
    fun release(@PathVariable resourceId: UUID, @Valid @RequestBody request: InventoryQuantityRequest) =
        inventory.releaseReservation(resourceId, request.quantity)

    @PostMapping("/issue")
    @Operation(summary = "Issue reserved quantity")
    fun issue(@PathVariable resourceId: UUID, @Valid @RequestBody request: InventoryQuantityRequest) =
        inventory.issue(resourceId, request.quantity)

    @PostMapping("/return")
    @Operation(summary = "Return issued quantity to stock")
    fun returnStock(@PathVariable resourceId: UUID, @Valid @RequestBody request: InventoryQuantityRequest) =
        inventory.returnStock(resourceId, request.quantity)
}
