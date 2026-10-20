package com.resourcetracker.approval

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import jakarta.validation.constraints.NotNull

enum class ApprovalDecision { APPROVE, REJECT, QUERY }

data class DecisionRequest(
    @field:NotNull
    val decision: ApprovalDecision,

    @field:Size(max = 1000, message = "Comment must be at most 1000 characters")
    @field:Schema(description = "Required for REJECT and QUERY; optional for APPROVE. Maximum 1000 characters.")
    val comment: String? = null,
)

data class DecisionResponse(
    val requestId: Int,
    val previousStatus: String,
    val newStatus: String,
    val decidedBy: Int,
    val comment: String?,
    val approvedLineIds: List<Int>,
    val rejectedLineIds: List<Int>
)
