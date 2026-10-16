package com.resourcetracker.approval

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/requests")
@Tag(name = "Approval workflow", description = "Approve, reject or query a pending resource request")
class AvailabilityController(
    private val availabilityService: AvailabilityService,
    private val currentUser: CurrentUser
) {

    @Operation(
        summary = "Stock review for a request's internal items",
        description = "Assigned manager only. Shows available, reserved and issued quantities, return dates, and whether each line can be fully or partly filled."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Availability per internal line"),
        ApiResponse(responseCode = "403", description = "Caller is not the assigned manager"),
        ApiResponse(responseCode = "404", description = "Request not found")
    )
    @GetMapping("/{id}/availability")
    fun availability(@PathVariable id: Int) = availabilityService.review(id, currentUser.id())
}