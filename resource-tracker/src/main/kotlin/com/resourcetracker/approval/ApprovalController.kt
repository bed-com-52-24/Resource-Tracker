package com.resourcetracker.approval

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/requests")
@Tag(name = "Approval workflow", description = "Approve, reject or query a pending resource request")
class ApprovalController(
    private val approvalService: ApprovalService,
    private val currentUser: CurrentUser
) {

    @Operation(
        summary = "Approve a pending request (fully or partially)",
        description = "Assigned manager only. Send no line lists to approve everything, or list every line item once as approved or rejected for a partial approval."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Decision recorded"),
        ApiResponse(responseCode = "400", description = "Bad comment or incomplete line list"),
        ApiResponse(responseCode = "403", description = "Caller is not the assigned manager"),
        ApiResponse(responseCode = "404", description = "Request not found"),
        ApiResponse(responseCode = "409", description = "Request is not PENDING, or was changed at the same time")
    )
    @PostMapping("/{id}/approve")
    fun approve(
        @PathVariable id: Int,
        @Valid @RequestBody(required = false) body: ApproveRequest?
    ) = approvalService.approve(id, currentUser.id(), body)

    @Operation(
        summary = "Reject a pending request",
        description = "Assigned manager only. A comment of 10 to 500 characters is required."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Request rejected"),
        ApiResponse(responseCode = "400", description = "Comment missing or not 10 to 500 characters"),
        ApiResponse(responseCode = "403", description = "Caller is not the assigned manager"),
        ApiResponse(responseCode = "404", description = "Request not found"),
        ApiResponse(responseCode = "409", description = "Request is not PENDING, or was changed at the same time")
    )
    @PostMapping("/{id}/reject")
    fun reject(
        @PathVariable id: Int,
        @RequestBody(required = false) body: DecisionRequest?
    ) = approvalService.reject(id, currentUser.id(), body?.comment)

    @Operation(
        summary = "Query a pending request",
        description = "Assigned manager only. A comment is required. Sends the request back to the team lead."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Request queried"),
        ApiResponse(responseCode = "400", description = "Comment missing"),
        ApiResponse(responseCode = "403", description = "Caller is not the assigned manager"),
        ApiResponse(responseCode = "404", description = "Request not found"),
        ApiResponse(responseCode = "409", description = "Request is not PENDING, or was changed at the same time")
    )
    @PostMapping("/{id}/query")
    fun query(
        @PathVariable id: Int,
        @RequestBody(required = false) body: DecisionRequest?
    ) = approvalService.query(id, currentUser.id(), body?.comment)
}