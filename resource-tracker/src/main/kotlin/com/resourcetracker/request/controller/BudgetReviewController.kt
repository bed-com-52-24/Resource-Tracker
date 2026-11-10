package com.resourcetracker.request.controller

import com.resourcetracker.request.service.BudgetReviewService
import com.resourcetracker.common.ErrorResponse
import com.resourcetracker.security.AuthenticatedUser
import com.resourcetracker.security.RequiresRoles
import com.resourcetracker.user.Role
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/requests")
@Tag(name = "Resource Requests", description = "Submit and manage resource requests")
@SecurityRequirement(name = "bearerAuth")
class BudgetReviewController(
    private val reviewService: BudgetReviewService,
) {
    @Operation(
        summary = "Budget review for a request's external items",
        description = "Assigned manager only. Shows the department's remaining budget, external item cost, and whether the cost exceeds the budget.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Budget review"),
        ApiResponse(responseCode = "401", description = "Authentication required", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Caller is not the assigned manager", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "Request not found", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "Department has no budget set up", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    )
    @GetMapping("/{id}/budget")
    @RequiresRoles(Role.DEPARTMENT_MANAGER)
    fun budget(
        @PathVariable id: Int,
        @AuthenticationPrincipal caller: AuthenticatedUser,
    ) = reviewService.review(id, caller.id)
}
