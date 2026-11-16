package com.resourcetracker.request.service

import com.resourcetracker.request.entity.ResourceRequestLineItem
import java.math.BigDecimal

data class LineDecision(
    val requestId: Int,
    val actorId: Int,
    val line: ResourceRequestLineItem,
    val approved: Boolean,
    val comment: String?,
    val budgetCost: BigDecimal? = null,
)

interface ExternalApprovalHandler {
    fun handle(decision: LineDecision)
}
