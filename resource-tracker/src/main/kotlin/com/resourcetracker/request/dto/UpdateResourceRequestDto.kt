package com.resourcetracker.request.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import io.swagger.v3.oas.annotations.media.Schema

enum class RequesterUpdateStatus { PENDING, CANCELLED }

data class UpdateResourceRequestDto(
    val status: RequesterUpdateStatus? = null,
    @field:Size(max = 1000) val comment: String? = null,
    @field:Valid val lineItems: List<RequestLineItemDto>? = null,
)

data class RequestDecisionDto(
    val decision: Decision,
    @field:Size(max = 1000) val comment: String? = null,
    val approvedQuantities: Map<Int, Int> = emptyMap(),
)

@Schema(description = "APPROVE reserves stock now; WAITLIST approves into the FIFO waitlist (internal lines only); REJECT/QUERY as before")
enum class Decision { QUERY, APPROVE, REJECT, WAITLIST }

data class ManagerReassignmentDto(val managerId: Int)

data class ApprovedQuantityUpdateRequest(
    @field:jakarta.validation.constraints.Positive val quantity: Int,
    @field:jakarta.validation.constraints.NotBlank @field:jakarta.validation.constraints.Size(max = 1000) val reason: String,
)
