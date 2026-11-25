package com.resourcetracker.request.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import io.swagger.v3.oas.annotations.media.Schema
import com.resourcetracker.request.entity.RequestPriority
import java.time.LocalDate

enum class RequesterUpdateStatus { PENDING, CANCELLED }

/** Shared PUT body. Fields are role-scoped by ResourceRequestService. */
data class UpdateRequestDto(
    @field:Schema(
        description = "TEAM_LEAD may send PENDING to clarify a QUERIED request, or CANCELLED from PENDING, QUERIED, APPROVED, PARTIALLY_APPROVED, or WAITLISTED (only before stock is issued). The assigned DEPARTMENT_MANAGER may send QUERIED, APPROVED, REJECTED, or WAITLISTED as allowed by the current status. PARTIALLY_APPROVED and COMPLETED are produced by the server, not sent here.",
        allowableValues = ["PENDING", "CANCELLED", "QUERIED", "APPROVED", "REJECTED", "WAITLISTED"],
        example = "APPROVED",
    )
    val status: com.resourcetracker.request.entity.RequestStatus? = null,
    @field:Schema(description = "TEAM_LEAD clarification/cancellation comment. Do not send for manager actions.", example = "Updated quantities as requested")
    @field:Size(max = 1000) val comment: String? = null,
    @field:Schema(description = "TEAM_LEAD only. Replace request lines while responding to a query; only editable while current request status is QUERIED.")
    @field:Valid val lineItems: List<RequestLineItemDto>? = null,
    @field:Schema(description = "DEPARTMENT_MANAGER only. Decision comment; required for QUERY and REJECT, and required for quantity adjustments or waivers.", example = "Approved for the project")
    @field:Size(max = 1000) val commentApproval: String? = null,
    @field:Schema(description = "DEPARTMENT_MANAGER only. For an initial APPROVED decision, list the line items being approved; omitted lines remain unapproved (quantity 0). For WAITLISTED, omitted lines use their full requested quantity. On APPROVED or PARTIALLY_APPROVED requests, listed quantities update those lines and the request status is recalculated. On WAITLISTED requests they waive outstanding amounts. expectedFulfillmentDate may be provided per entry.")
    @field:Valid val approvedQuantities: List<ApprovedQuantityDecision> = emptyList(),
    @field:Schema(description = "DEPARTMENT_MANAGER only. Set request priority within your department.", example = "HIGH")
    val priority: RequestPriority? = null,
)

@Schema(description = "Team-lead edit: use PENDING to clarify/edit or CANCELLED to cancel. Line items are optional when editing.")
data class UpdateResourceRequestDto(
    @field:Schema(allowableValues = ["PENDING", "CANCELLED"], example = "PENDING")
    val status: RequesterUpdateStatus? = null,
    @field:Size(max = 1000) val comment: String? = null,
    @field:Valid val lineItems: List<RequestLineItemDto>? = null,
)

data class ApprovedQuantityDecision(
    @field:Positive val lineItemId: Int,
    @field:jakarta.validation.constraints.PositiveOrZero val quantity: Int,
    @field:Schema(description = "Planning target date for any quantity that cannot be fulfilled immediately (backorder/waitlist). Does not affect stock reservation or issuance.", example = "2026-07-15")
    val expectedFulfillmentDate: LocalDate? = null,
    @field:Positive
    @field:Schema(description = "Manager-approved duration in days from the actual stock issue date. Defaults to the requester's requested duration.", example = "21")
    val approvedDurationDays: Int? = null,
)

@Schema(description = "Approved quantities per request line item. For a partial approval, include only the lines being approved; omitted lines remain unapproved. Use quantity 0 to explicitly leave a line unapproved. The request remains PARTIALLY_APPROVED until every line reaches its requested quantity; later manager updates can approve the remaining quantities. The server calculates whether the resulting request is APPROVED or PARTIALLY_APPROVED.")
data class RequestDecisionDto(
    @field:Schema(allowableValues = ["QUERY", "APPROVE", "REJECT", "WAITLIST"], example = "APPROVE")
    val decision: Decision,
    @field:Size(max = 1000) val comment: String? = null,
    @field:Schema(description = "For APPROVE, list the line items being approved; omitted lines remain unapproved. An empty list approves all lines. For WAITLIST, omitted lines use their full requested quantities.", example = "[{\"lineItemId\": 1, \"quantity\": 4}]")
    @field:Valid val approvedQuantities: List<ApprovedQuantityDecision> = emptyList(),
)

@Schema(description = "Manager decision for a PENDING or QUERIED request. QUERY is available from PENDING; APPROVE, REJECT, and WAITLIST are available from PENDING; REJECT is also available from QUERIED. APPROVE reserves available internal stock and authorizes external procurement unless a budget exception is pending. WAITLIST places internal lines in the fulfillment queue.")
enum class Decision { QUERY, APPROVE, REJECT, WAITLIST }

data class ManagerReassignmentDto(val managerId: Int)

data class ApprovedQuantityUpdateRequest(
    @field:jakarta.validation.constraints.Positive val quantity: Int,
    @field:jakarta.validation.constraints.NotBlank @field:jakarta.validation.constraints.Size(max = 1000) val reason: String,
)

data class WaiveLineBalanceRequest(
    @field:jakarta.validation.constraints.Positive val quantity: Int,
    @field:jakarta.validation.constraints.NotBlank @field:jakarta.validation.constraints.Size(max = 1000) val reason: String,
)

data class UpdateExpectedFulfillmentDateRequest(
    val expectedFulfillmentDate: LocalDate,
)
