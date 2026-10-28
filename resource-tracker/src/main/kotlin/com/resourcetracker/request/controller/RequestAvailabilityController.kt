package com.resourcetracker.request.controller

import com.resourcetracker.request.service.AvailabilityService
import com.resourcetracker.security.AuthenticatedUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/requests")
@Tag(name = "Resource Requests", description = "Submit and manage resource requests")
class RequestAvailabilityController(
    private val availabilityService: AvailabilityService,
) {
    @Operation(
        summary = "Stock review for a request's internal items",
        description = "Assigned manager only. Shows available, reserved and issued quantities, expected return dates, and whether each line can be fully or partly filled.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Availability per internal line"),
        ApiResponse(responseCode = "403", description = "Caller is not the assigned manager"),
        ApiResponse(responseCode = "404", description = "Request not found"),
    )
    @GetMapping("/{id}/availability")
    fun availability(
        @PathVariable id: Int,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = availabilityService.review(id, caller.id)
}
