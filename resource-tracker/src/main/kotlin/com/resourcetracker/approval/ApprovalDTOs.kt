package com.resourcetracker.approval

import jakarta.validation.constraints.Size

/** What the manager sends. For approve the comment is optional (SRS 2.4.4.5). */
data class DecisionRequest(
    @field:Size(max = 500, message = "Comment must be at most 500 characters")
    val comment: String? = null
)

/** What the API returns after a decision. */
data class DecisionResponse(
    val requestId: Int,
    val previousStatus: String,
    val newStatus: String,
    val decidedBy: Int,
    val comment: String?
)
