package com.resourcetracker.request.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.Size

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

enum class Decision { APPROVE, REJECT, QUERY }

data class ManagerReassignmentDto(val managerId: Int)

data class ApprovedQuantityUpdateRequest(
    @field:jakarta.validation.constraints.Positive val quantity: Int,
    @field:jakarta.validation.constraints.NotBlank @field:jakarta.validation.constraints.Size(max = 1000) val reason: String,
)
