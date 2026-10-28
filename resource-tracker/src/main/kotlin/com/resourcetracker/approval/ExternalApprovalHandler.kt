package com.resourcetracker.approval

import com.resourcetracker.request.entity.ResourceRequestLineItem

data class LineDecision(
    val requestId: Int,
    val actorId: Int,
    val line: ResourceRequestLineItem,
    val approved: Boolean,
    val comment: String?,
)

interface ExternalApprovalHandler {
    fun handle(decision: LineDecision)
}
