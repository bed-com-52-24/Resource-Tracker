package com.resourcetracker.request.service

import com.resourcetracker.request.entity.RequestHistory
import com.resourcetracker.request.entity.RequestStatus
import com.resourcetracker.request.entity.ResourceRequestLineItem
import com.resourcetracker.request.entity.WaitlistEntry
import com.resourcetracker.request.repository.RequestHistoryRepository
import com.resourcetracker.request.repository.WaitlistEntryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WaitlistService(
    private val entries: WaitlistEntryRepository,
    private val history: RequestHistoryRepository,
) {
    @Transactional
    fun enqueue(
        requestId: Int,
        lines: List<ResourceRequestLineItem>,
        actorId: Int,
        from: RequestStatus,
        comment: String?,
    ) {
        val waitlistedLines = lines.map { line ->
            val lineId = requireNotNull(line.lineItemId)
            val resourceId = requireNotNull(line.resourceId)
            val approvedQuantity = requireNotNull(line.approvedQuantity)
            WaitlistEntry(
                requestId = requestId,
                lineItemId = lineId,
                resourceId = resourceId,
                quantity = approvedQuantity,
            ) to line
        }
        entries.saveAll(waitlistedLines.map { it.first })
        waitlistedLines.forEach { (entry, line) ->
            history.save(
                RequestHistory(
                    requestId = requestId,
                    eventType = "WAITLIST_CREATED",
                    fromStatus = from.name,
                    toStatus = RequestStatus.WAITLISTED.name,
                    actorId = actorId,
                    lineItemId = line.lineItemId,
                    quantity = entry.quantity,
                    comment = comment,
                ),
            )
        }
    }
}
