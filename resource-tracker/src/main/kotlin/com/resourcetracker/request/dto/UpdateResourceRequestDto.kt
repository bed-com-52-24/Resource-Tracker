package com.resourcetracker.request.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import io.swagger.v3.oas.annotations.media.Schema

enum class RequesterUpdateStatus { PENDING, CANCELLED }

@Schema(description = "Team-lead edit: use PENDING to clarify/edit or CANCELLED to cancel. Line items are optional when editing.")
data class UpdateResourceRequestDto(
    @field:Schema(allowableValues = ["PENDING", "CANCELLED"], example = "PENDING")
    val status: RequesterUpdateStatus? = null,
    @field:Size(max = 1000) val comment: String? = null,
    @field:Valid val lineItems: List<RequestLineItemDto>? = null,
)

data class ApprovedQuantityDecision(
    @field:Positive val lineItemId: Int,
    @field:Positive val quantity: Int,
)

@Schema(description = "Manager decision. QUERY requires a comment; APPROVE/WAITLIST run stock checks; REJECT requires a comment. WAITLIST is for internal lines only.")
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
