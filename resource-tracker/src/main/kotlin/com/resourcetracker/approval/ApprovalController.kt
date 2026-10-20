package com.resourcetracker.approval

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/requests")
@Tag(name = "Approval workflow", description = "Approve, reject or query a pending resource request")
@SecurityRequirement(name = "bearerAuth")
class ApprovalController(
    private val approvalService: ApprovalService,
    private val currentUser: CurrentUser
) {

    @Operation(summary = "Record a request decision", description = "Assigned manager only. APPROVE or REJECT applies to all request lines; QUERY returns the request for more information. REJECT and QUERY require a comment.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Decision recorded"),
        ApiResponse(responseCode = "400", description = "Invalid decision body or comment"),
        ApiResponse(responseCode = "403", description = "Caller is not the assigned manager"),
        ApiResponse(responseCode = "404", description = "Request not found"),
        ApiResponse(responseCode = "409", description = "Request is not PENDING, or was changed at the same time")
    )
    @PostMapping("/{id}/decision")
    fun decide(
        @PathVariable id: Int,
        @Valid @RequestBody body: DecisionRequest
    ) = approvalService.decide(id, currentUser.id(), body)
}
