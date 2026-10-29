package com.resourcetracker.request.service

import com.resourcetracker.audit.AuditAction
import com.resourcetracker.audit.AuditRecord
import com.resourcetracker.audit.AuditService
import com.resourcetracker.notification.NotificationService
import com.resourcetracker.notification.NotificationType
import com.resourcetracker.request.entity.FulfillmentPreference
import com.resourcetracker.request.entity.RequestHistory
import com.resourcetracker.request.entity.RequestStatus
import com.resourcetracker.request.entity.ProcurementStatus
import com.resourcetracker.request.entity.RequestTransitions
import com.resourcetracker.request.repository.ProcurementRepository
import com.resourcetracker.request.repository.RequestHistoryRepository
import com.resourcetracker.request.repository.RequestLineItemRepository
import com.resourcetracker.request.repository.ResourceRequestRepository
import com.resourcetracker.request.repository.StockIssueRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class RequestCompletionService(
    private val requests: ResourceRequestRepository,
    private val lines: RequestLineItemRepository,
    private val issues: StockIssueRepository,
    private val procurements: ProcurementRepository,
    private val history: RequestHistoryRepository,
    private val audit: AuditService,
    private val notifications: NotificationService,
) {
    @Transactional(propagation = Propagation.MANDATORY)
    fun completeIfFulfilled(requestId: Int, actorId: Int): Boolean {
        val request = requests.findForUpdateById(requestId) ?: return false
        val target = RequestTransitions.completionTarget(request.status) ?: return false
        val requestLines = lines.findAllByRequestId(requestId)
        if (requestLines.isEmpty()) return false

        val issuesByLine = issues.findAllByRequestId(requestId)
            .groupBy { it.lineItemId }
            .mapValues { (_, lineIssues) -> lineIssues.sumOf { it.quantity.toLong() } }
        val procurementsByLine = procurements.findAllByRequestId(requestId)
            .groupBy { it.lineItemId }

        val fulfilled = requestLines.all { line ->
            val lineId = requireNotNull(line.lineItemId)
            val approvedQuantity = (line.approvedQuantity ?: line.quantity).toLong()
            if (line.fulfillmentPreference == FulfillmentPreference.EXTERNAL.name) {
                procurementsByLine[lineId].orEmpty()
                    .filter { it.status == ProcurementStatus.ACCEPTED }
                    .sumOf { (it.receivedQuantity ?: 0).toLong() } >= approvedQuantity
            } else {
                (issuesByLine[lineId] ?: 0L) >= approvedQuantity
            }
        }
        if (!fulfilled) return false

        val previousStatus = request.status
        request.status = target
        requests.saveAndFlush(request)
        history.save(
            RequestHistory(
                requestId = requestId,
                eventType = target.name,
                fromStatus = previousStatus.name,
                toStatus = target.name,
                actorId = actorId,
            ),
        )
        audit.record(
            AuditRecord.currentActor(
                action = AuditAction.REQUEST_COMPLETED,
                itemType = "REQUEST",
                itemId = requestId.toString(),
                previousState = mapOf("status" to previousStatus.name),
                newState = mapOf("status" to target.name),
                changedFields = listOf("status"),
            ),
        )
        notifications.notify(
            listOf(request.requesterId),
            NotificationType.COMPLETED,
            mapOf("requestId" to requestId, "requestNumber" to request.requestNumber),
        )
        return true
    }
}
