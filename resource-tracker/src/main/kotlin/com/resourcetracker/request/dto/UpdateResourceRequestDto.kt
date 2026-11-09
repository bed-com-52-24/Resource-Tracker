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
    @field:Schema(description = "TEAM_LEAD: PENDING to clarify or CANCELLED to cancel. DEPARTMENT_MANAGER: QUERIED, APPROVED, REJECTED, or WAITLISTED. PARTIALLY_APPROVED is calculated when approved quantities are reduced.", example = "APPROVED")
    val status: com.resourcetracker.request.entity.RequestStatus? = null,
    @field:Schema(description = "TEAM_LEAD clarification/cancellation comment. Do not send for manager actions.", example = "Updated quantities as requested")
    @field:Size(max = 1000) val comment: String? = null,
    @field:Schema(description = "TEAM_LEAD only. Replace request lines while responding to a query; only editable while current request status is QUERIED.")
    @field:Valid val lineItems: List<RequestLineItemDto>? = null,
    @field:Schema(description = "DEPARTMENT_MANAGER only. Decision comment; required for QUERY and REJECT, and required for quantity adjustments or waivers.", example = "Approved for the project")
    @field:Size(max = 1000) val commentApproval: String? = null,
    @field:Schema(description = "DEPARTMENT_MANAGER only. Required quantities for initial APPROVED/WAITLISTED decisions. On an already APPROVED request these adjust approved amounts; on an already WAITLISTED request they waive outstanding amounts. expectedFulfillmentDate may be provided per entry.")
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

@Schema(description = "Approved quantities per request line item. Omit an entry to approve the full requested quantity. Use quantity 0 to reject a line item; approving fewer than requested sets the request status to PARTIALLY_APPROVED.")
data class RequestDecisionDto(
    @field:Schema(allowableValues = ["QUERY", "APPROVE", "REJECT", "WAITLIST"], example = "APPROVE")
    val decision: Decision,
    @field:Size(max = 1000) val comment: String? = null,
    @field:Schema(description = "Approved quantities per request line item. Omit or leave empty to approve full requested quantities.", example = "[{\"lineItemId\": 1, \"quantity\": 4}]")
    @field:Valid val approvedQuantities: List<ApprovedQuantityDecision> = emptyList(),
)

@Schema(description = "APPROVE reserves stock now; WAITLIST approves into the FIFO waitlist (internal lines only); REJECT/QUERY as before")
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
