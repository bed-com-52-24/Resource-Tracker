package com.resourcetracker.request.service

import com.resourcetracker.audit.AuditAction
import com.resourcetracker.audit.AuditRecord
import com.resourcetracker.audit.AuditService
import com.resourcetracker.notification.NotificationService
import com.resourcetracker.notification.NotificationType
import com.resourcetracker.request.entity.BudgetOverride
import com.resourcetracker.request.entity.BudgetOverrideStatus
import com.resourcetracker.request.entity.RequestHistory
import com.resourcetracker.request.entity.RequestStatus
import com.resourcetracker.request.repository.BudgetOverrideRepository
import com.resourcetracker.request.repository.ResourceRequestRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

data class BudgetOverrideDecision(val decision: BudgetOverrideStatus, val comment: String)

data class BudgetOverrideResponse(
    val id: Int,
    val requestId: Int,
    val lineItemId: Int,
    val departmentId: Int,
    val requestedBy: Int,
    val amount: java.math.BigDecimal,
    val requestedQuantity: Int,
    val reason: String,
    val status: BudgetOverrideStatus,
    val createdAt: LocalDateTime?,
    val decidedBy: Int?,
    val decisionComment: String?,
    val decidedAt: LocalDateTime?,
)

@Service
class BudgetOverrideService(
    private val overrides: BudgetOverrideRepository,
    private val requests: ResourceRequestRepository,
    private val budget: BudgetPort,
    private val procurements: ProcurementService,
    private val history: com.resourcetracker.request.repository.RequestHistoryRepository,
    private val audit: AuditService,
    private val notifications: NotificationService,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun pending(): List<BudgetOverrideResponse> = overrides
        .findAllByStatusOrderByCreatedAtAsc(BudgetOverrideStatus.PENDING)
        .map { it.toResponse() }

    @Transactional
    fun decide(id: Int, actorId: Int, decision: BudgetOverrideDecision): BudgetOverrideResponse {
        if (decision.decision !in setOf(BudgetOverrideStatus.APPROVED, BudgetOverrideStatus.REJECTED)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Decision must be APPROVED or REJECTED")
        }
        val snapshot = overrides.findById(id).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Budget override not found")
        // Lock the request before the override, matching request-cancellation lock order.
        val request = requests.findForUpdateById(snapshot.requestId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found")
        val override = overrides.findForUpdateById(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Budget override not found")
        if (override.requestId != request.requestId) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Budget override request changed")
        }
        if (override.status != BudgetOverrideStatus.PENDING) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Budget override has already been decided or cancelled")
        }
        if (request.status !in setOf(RequestStatus.APPROVED, RequestStatus.PARTIALLY_APPROVED, RequestStatus.WAITLISTED)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "The manager-approved request is no longer eligible for this override")
        }

        val action: AuditAction
        if (decision.decision == BudgetOverrideStatus.APPROVED) {
            val date = request.requiredDate ?: LocalDate.now(clock)
            val currentBudget = budget.status(override.departmentId, date)
                ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Department has no active budget for the request date")
            budget.forceCommit(override.departmentId, date, override.amount)
            procurements.authorizeApprovedOverride(override, actorId)
            action = AuditAction.BUDGET_OVERRIDE_APPROVED
            // Keep the commit itself separately visible in the immutable audit stream.
            audit.record(
                AuditRecord(
                    actorId = actorId,
                    action = AuditAction.BUDGET_COMMITTED,
                    itemType = "DEPARTMENT_BUDGET",
                    itemId = override.departmentId.toString(),
                    newState = mapOf("overrideId" to id, "requestId" to override.requestId, "lineItemId" to override.lineItemId),
                    comment = "Override approved: committed ${override.amount}; remaining before approval ${currentBudget.remaining}".take(500),
                ),
            )
        } else {
            action = AuditAction.BUDGET_OVERRIDE_REJECTED
        }

        val previous = override.status
        override.status = decision.decision
        override.decidedBy = actorId
        override.decisionComment = decision.comment.trim()
        override.decidedAt = LocalDateTime.now(clock)
        val saved = overrides.save(override)
        history.save(
            RequestHistory(
                requestId = override.requestId,
                eventType = "BUDGET_OVERRIDE_${decision.decision.name}",
                fromStatus = previous.name,
                toStatus = decision.decision.name,
                actorId = actorId,
                lineItemId = override.lineItemId,
                quantity = override.requestedQuantity,
                comment = "Override $id: ${decision.comment.trim()}",
            ),
        )
        audit.record(
            AuditRecord(
                actorId = actorId,
                action = action,
                itemType = "DEPARTMENT_BUDGET",
                itemId = override.departmentId.toString(),
                previousState = mapOf("status" to previous.name),
                newState = mapOf("overrideId" to id, "requestId" to override.requestId, "lineItemId" to override.lineItemId, "status" to decision.decision.name),
                comment = decision.comment.trim().take(500),
            ),
        )
        notifications.notify(
            listOfNotNull(request.requesterId, request.assignedManagerId),
            NotificationType.BUDGET_OVERRIDE_DECIDED,
            mapOf("requestId" to request.requestId, "requestNumber" to request.requestNumber, "overrideId" to id, "decision" to decision.decision.name),
        )
        return saved.toResponse()
    }

    private fun BudgetOverride.toResponse() = BudgetOverrideResponse(
        id, requestId, lineItemId, departmentId, requestedBy, amount, requestedQuantity,
        reason, status, createdAt, decidedBy, decisionComment, decidedAt,
    )
}
