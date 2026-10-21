package com.resourcetracker.inventory

import com.resourcetracker.common.ErrorResponse
import com.resourcetracker.resource.ResourceCategory
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/resources")
@Tag(name = "Availability", description = "Availability of INTERNAL resources")
@SecurityRequirement(name = "bearerAuth")
class AvailabilityController(private val inventory: InventoryService) {
    @GetMapping("/{id}/availability")
    @Operation(summary = "Get resource availability", description = "Available, reserved and issued totals across locations. nextExpectedAvailability is null until RT-18 provides due-return dates.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Availability returned", content = [Content(schema = Schema(implementation = ResourceAvailabilityResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Resource not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "Resource is not INTERNAL", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun one(@PathVariable id: Int) = inventory.availability(id)

    @GetMapping("/availability")
    @Operation(summary = "List resource availability", description = "Lists active INTERNAL resources. AVAILABLE means all units are free; PARTIALLY_AVAILABLE means some units are free while some are reserved or issued; FULLY_RESERVED means none are free and some are reserved; OUT_OF_STOCK means none are free and none are reserved. Filter by status, department id, or category.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Availability list returned"),
        ApiResponse(responseCode = "400", description = "Invalid filter", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    ])
    fun list(
        @RequestParam(required = false) status: AvailabilityStatus?,
        @RequestParam(required = false) departmentId: Int?,
        @RequestParam(required = false) category: ResourceCategory?,
    ) = inventory.availabilityList(departmentId, category, status)
}
