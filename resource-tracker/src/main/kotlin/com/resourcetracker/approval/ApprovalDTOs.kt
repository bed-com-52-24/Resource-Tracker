package com.resourcetracker.approval

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.Size

/** Body for reject and query. */
data class DecisionRequest(
    @field:Schema(description = "Reason. Reject needs 10 to 500 characters; query needs 1 to 500.")
    val comment: String? = null
)

/** One rejected line item and why. */
data class RejectedLine(
    val lineId: Int,
    @field:Size(min = 10, max = 500, message = "A rejected line needs a comment of 10 to 500 characters")
    val comment: String
)

/** Body for approve. Leave both lists empty to approve every line item. */
data class ApproveRequest(
    @field:Size(max = 500, message = "Comment must be at most 500 characters")
    val comment: String? = null,

    @field:Schema(description = "If true, internal lines with too little stock are approved as waitlisted instead of failing with 409.")
    val waitlist: Boolean = false,

    @field:Schema(description = "Line items to approve. For partial approval, list every line item once, either here or in rejectedLines.")
    val approvedLineIds: List<Int> = emptyList(),

    @field:Valid
    val rejectedLines: List<RejectedLine> = emptyList()
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