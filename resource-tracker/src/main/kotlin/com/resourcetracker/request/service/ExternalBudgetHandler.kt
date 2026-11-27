package com.resourcetracker.request.service

import com.resourcetracker.audit.AuditAction
import com.resourcetracker.audit.AuditRecord
import com.resourcetracker.audit.AuditService
import com.resourcetracker.notification.NotificationService
import com.resourcetracker.notification.NotificationType
import com.resourcetracker.request.entity.BudgetOverride
import com.resourcetracker.request.entity.BudgetOverrideStatus
import com.resourcetracker.request.entity.FulfillmentPreference
import com.resourcetracker.request.repository.BudgetOverrideRepository
import com.resourcetracker.request.repository.RequestHistoryRepository
import com.resourcetracker.user.Role
import com.resourcetracker.user.UserRepository
import java.time.LocalDate
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
@Component
class ExternalBudgetHandler(
    private val budget: BudgetPort,
    private val auditService: AuditService,
    private val overrides: BudgetOverrideRepository,
    private val history: RequestHistoryRepository,
    private val users: UserRepository,
    private val notifications: NotificationService,
) : ExternalApprovalHandler {
    override fun handle(decision: LineDecision) {
        if (!decision.approved || decision.line.fulfillmentPreference != FulfillmentPreference.EXTERNAL.name) return

        val departmentId = budget.departmentOfRequest(decision.requestId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Request ${decision.requestId} not found")
        val date = budget.requestDateOfRequest(decision.requestId) ?: LocalDate.now()
        val before = budget.status(departmentId, date)
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Department $departmentId has no budget set up")
        val lineId = requireNotNull(decision.line.lineItemId)
        val cost = decision.budgetCost ?: budget.lineCost(lineId)
        val overrideComment = decision.comment?.trim().orEmpty()

        val latestOverride = overrides.findFirstByLineItemIdOrderByIdDesc(lineId)
        if (latestOverride?.status == BudgetOverrideStatus.PENDING) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "A budget override is already awaiting Managing Director review")
        }

        if (budget.tryCommit(departmentId, date, cost)) {
            if (latestOverride?.status == BudgetOverrideStatus.REJECTED) {
                overrides.save(
                    BudgetOverride(
                        requestId = decision.requestId,
                        lineItemId = lineId,
                        departmentId = departmentId,
                        requestedBy = decision.actorId,
                        amount = cost,
                        requestedQuantity = decision.quantity ?: (decision.line.approvedQuantity ?: decision.line.quantity),
                        reason = "Budget fit on manager resubmission",
                        status = BudgetOverrideStatus.APPROVED,
                        decidedBy = decision.actorId,
                        decisionComment = "Regular budget check passed; no override used",
                    ),
                )
            }
            audit(decision, departmentId, AuditAction.BUDGET_COMMITTED, committedComment(lineId, before, cost))
            return
        }

        if (overrideComment.length !in 10..500) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Approval exceeds the department budget: cost $cost, remaining ${before.remaining}. Submit an override reason between 10 and 500 characters for Managing Director review.",
            )
        }

        val override = overrides.save(
            BudgetOverride(
                requestId = decision.requestId,
                lineItemId = lineId,
                departmentId = departmentId,
                requestedBy = decision.actorId,
                amount = cost,
                requestedQuantity = decision.quantity ?: (decision.line.approvedQuantity ?: decision.line.quantity),
                reason = overrideComment,
            ),
        )
        history.save(
            com.resourcetracker.request.entity.RequestHistory(
                requestId = decision.requestId,
                eventType = "BUDGET_OVERRIDE_REQUESTED",
                fromStatus = null,
                toStatus = BudgetOverrideStatus.PENDING.name,
                actorId = decision.actorId,
                lineItemId = lineId,
                comment = "Override ${override.id} requested for $cost (remaining ${before.remaining}). $overrideComment",
            ),
        )
        audit(
            decision,
            departmentId,
            AuditAction.BUDGET_OVERRIDE_REQUESTED,
            "Override ${override.id}; line $lineId; amount $cost; remaining ${before.remaining}. Reason: $overrideComment",
        )
        notifications.notify(
            users.findActiveByRole(Role.MANAGING_DIRECTOR).map { it.id },
            NotificationType.BUDGET_OVERRIDE_REQUIRED,
            mapOf(
                "requestId" to decision.requestId,
                "requestNumber" to budget.requestNumberOfRequest(decision.requestId),
                "lineItemId" to lineId,
                "overrideId" to override.id,
            ),
        )
        notifications.notify(
            users.findActiveByRoleAndDepartmentId(Role.DEPARTMENT_MANAGER, departmentId).map { it.id },
            NotificationType.BUDGET_OVERRIDE_ROUTED,
            mapOf(
                "requestId" to decision.requestId,
                "requestNumber" to budget.requestNumberOfRequest(decision.requestId),
                "lineItemId" to lineId,
                "overrideId" to override.id,
            ),
        )
    }

    private fun committedComment(lineId: Int, before: BudgetStatus, cost: java.math.BigDecimal): String =
        "Line $lineId: committed ${before.committed} -> ${before.committed + cost}, " +
            "remaining ${before.remaining} -> ${before.remaining - cost}"

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
