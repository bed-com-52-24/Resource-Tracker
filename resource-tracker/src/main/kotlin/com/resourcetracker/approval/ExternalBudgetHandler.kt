package com.resourcetracker.approval

import com.resourcetracker.audit.AuditAction
import com.resourcetracker.audit.AuditRecord
import com.resourcetracker.audit.AuditService
import com.resourcetracker.request.entity.FulfillmentPreference
import com.resourcetracker.request.entity.ResourceRequestLineItem
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal

/**
 * Runs for every approved EXTERNAL line, inside the approval transaction.
 * If it throws, the status change rolls back with it, so budget and status always agree.
 */
@Component
class ExternalBudgetHandler(
    private val budget: BudgetPort,
    private val audit: AuditService,
    private val procurement: ProcurementNotifier
) {

    fun commitApprovedLines(
        requestId: Int,
        lines: List<ResourceRequestLineItem>,
        actorId: Int,
        comment: String?,
    ) {
        val externalLines = lines.filter {
            it.fulfillmentPreference == FulfillmentPreference.EXTERNAL.name
        }
        if (externalLines.isEmpty()) return

        val departmentId = budget.departmentOfRequest(requestId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Request $requestId not found")
        val overrideComment = comment?.trim().orEmpty()

        externalLines.forEach { line ->
            val lineId = requireNotNull(line.lineItemId) { "Approved line item must be persisted" }
            val quantity = line.approvedQuantity ?: line.quantity
            val amount = line.unitPrice.multiply(BigDecimal.valueOf(quantity.toLong()))
            val before = budget.status(departmentId)
                ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Department $departmentId has no budget set up")
            val committed = budget.tryCommit(departmentId, amount)

            if (!committed) {
                if (overrideComment.isEmpty()) {
                    throw ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Approval exceeds the department budget: cost $amount, remaining ${before.remaining}. " +
                            "Add a comment to override.",
                    )
                }
                budget.forceCommit(departmentId, amount)
            }

            audit.record(
                AuditRecord(
                    actorId = actorId,
                    action = if (committed) {
                        AuditAction.BUDGET_ALLOCATION_CHANGED
                    } else {
                        AuditAction.POLICY_OVERRIDE_APPROVED
                    },
                    itemType = "RESOURCE_REQUEST_LINE",
                    itemId = lineId.toString(),
                    previousState = before,
                    newState = BudgetStatus(departmentId, before.budget, before.committed + amount),
                    comment = if (committed) {
                        "Budget committed for request $requestId"
                    } else {
                        overrideComment
                    },
                ),
            )
            procurement.itemApproved(requestId, lineId, amount)
        }
    }
}