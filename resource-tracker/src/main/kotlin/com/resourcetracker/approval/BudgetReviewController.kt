package com.resourcetracker.approval

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/requests")
@Tag(name = "Approval workflow", description = "Approve, reject or query a pending resource request")
class BudgetReviewController(
    private val reviewService: BudgetReviewService,
    private val currentUser: CurrentUser
) {

    @Operation(
        summary = "Budget review for a request's external items",
        description = "Assigned manager only. Shows the department's remaining budget, the cost of the external items, and whether approving exceeds the budget."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Budget review"),
        ApiResponse(responseCode = "403", description = "Caller is not the assigned manager"),
        ApiResponse(responseCode = "404", description = "Request not found"),
        ApiResponse(responseCode = "409", description = "Department has no budget set up")
    )
    @GetMapping("/{id}/budget")
    fun budget(@PathVariable id: Int) = reviewService.review(id, currentUser.id())
}