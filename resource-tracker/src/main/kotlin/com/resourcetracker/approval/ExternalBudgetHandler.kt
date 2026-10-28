package com.resourcetracker.approval

import com.resourcetracker.audit.AuditAction
import com.resourcetracker.audit.AuditRecord
import com.resourcetracker.audit.AuditService
import com.resourcetracker.request.entity.FulfillmentPreference
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

/**
 * Runs for every approved EXTERNAL line, inside the approval transaction.
 * If it throws, the status change rolls back with it, so budget and status always agree.
 */
@Component
class ExternalBudgetHandler(
    private val budget: BudgetPort,
    private val auditService: AuditService,
    private val procurement: ProcurementNotifier
) : ExternalApprovalHandler {

    override fun handle(decision: LineDecision) {
        if (!decision.approved || decision.line.fulfillmentPreference != FulfillmentPreference.EXTERNAL.name) return

        val departmentId = budget.departmentOfRequest(decision.requestId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Request ${decision.requestId} not found")
        val before = budget.status(departmentId)
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Department $departmentId has no budget set up")

        val lineId = requireNotNull(decision.line.lineItemId)
        val cost = budget.lineCost(lineId)
        val overrideComment = decision.comment?.trim().orEmpty()

        if (budget.tryCommit(departmentId, cost)) {
            audit(decision, departmentId, AuditAction.BUDGET_COMMITTED,
                "Line $lineId: committed ${before.committed} -> ${before.committed + cost}, " +
                        "remaining ${before.remaining} -> ${before.remaining - cost}")
        } else {
            if (overrideComment.isEmpty()) {
                throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Approval exceeds the department budget: cost $cost, remaining ${before.remaining}. " +
                            "Add a comment to override."
                )
            }
            budget.forceCommit(departmentId, cost)
            audit(decision, departmentId, AuditAction.BUDGET_COMMITTED,
                "Line $lineId: committed ${before.committed} -> ${before.committed + cost}, " +
                        "remaining ${before.remaining} -> ${before.remaining - cost}")
            audit(decision, departmentId, AuditAction.BUDGET_OVERRIDE_USED,
                "Line $lineId: cost $cost, remaining ${before.remaining}. Reason: $overrideComment")
        }

        procurement.itemApproved(decision.requestId, lineId, cost)
    }

    private fun audit(decision: LineDecision, departmentId: Int, action: AuditAction, comment: String) {
        auditService.record(
            AuditRecord(
                actorId = decision.actorId,
                action = action,
                itemType = "DEPARTMENT_BUDGET",
                itemId = departmentId.toString(),
                newState = mapOf("requestId" to decision.requestId, "lineItemId" to decision.line.lineItemId),
                comment = comment.take(500),
            ),
        )
    }
}