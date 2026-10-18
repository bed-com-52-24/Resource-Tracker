package com.resourcetracker.approval

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
    private val auditRepository: ApprovalAuditRepository,
    private val procurement: ProcurementNotifier
) : ExternalApprovalHandler {

    override fun handle(decision: LineDecision) {
        if (!decision.approved) return          // rejection commits nothing

        val departmentId = budget.departmentOfRequest(decision.requestId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Request ${decision.requestId} not found")
        val before = budget.status(departmentId)
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Department $departmentId has no budget set up")

        val cost = budget.lineCost(decision.line.id)
        val overrideComment = decision.comment?.trim().orEmpty()

        if (budget.tryCommit(departmentId, cost)) {
            audit(decision, "BUDGET_COMMITTED",
                "Line ${decision.line.id}: committed ${before.committed} -> ${before.committed + cost}, " +
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
            audit(decision, "BUDGET_COMMITTED",
                "Line ${decision.line.id}: committed ${before.committed} -> ${before.committed + cost}, " +
                        "remaining ${before.remaining} -> ${before.remaining - cost}")
            audit(decision, "BUDGET_OVERRIDE_USED",
                "Line ${decision.line.id}: cost $cost, remaining ${before.remaining}. Reason: $overrideComment")
        }

        procurement.itemApproved(decision.requestId, decision.line.id, cost)
    }

    private fun audit(decision: LineDecision, action: String, comment: String) {
        auditRepository.save(
            ApprovalAudit(
                requestId = decision.requestId, actorId = decision.actorId,
                previousState = null, newState = "COMMITTED", action = action, comment = comment.take(500)
            )
        )
    }
}