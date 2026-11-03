package com.resourcetracker.notification

import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class NotificationService(
    private val notifications: NotificationRepository,
) {

    @Transactional(propagation = Propagation.MANDATORY)
    fun notify(userIds: Collection<Int>, type: NotificationType, payload: Map<String, Any?>) {
        val requestId = payload["requestId"] as? Int
        val message = buildMessage(type, payload)
        val entries = userIds.distinct().map { userId ->
            Notification(
                recipientId = userId,
                type = type,
                requestId = requestId,
                message = message,
            )
        }

        if (entries.isNotEmpty()) {
            notifications.saveAll(entries)
        }
    }

    @Transactional(readOnly = true)
    fun listForUser(
        userId: Int,
        status: NotificationStatus? = null,
        search: String?,
        pageable: Pageable,
    ): List<NotificationResponse> {
        val normalized = search?.trim()?.takeIf { it.isNotEmpty() }
        val page = when {
            normalized != null -> notifications.searchForUser(userId, status, normalized, pageable)
            status != null -> notifications.findByRecipientIdAndStatusOrderByCreatedAtDesc(userId, status, pageable)
            else -> notifications.findByRecipientIdOrderByCreatedAtDesc(userId, pageable)
        }
        return page.content.map { it.toResponse() }
    }

    @Transactional
    fun updateReadState(userId: Int, request: NotificationUpdateRequest): NotificationUpdateResponse {
        val hasIds = request.ids != null
        if ((hasIds && request.all) || (!hasIds && !request.all) || request.ids?.isEmpty() == true) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Provide non-empty ids or set all=true, but not both"
            )
        }

        if (request.all) {
            val readAt = if (request.status == NotificationStatus.READ) Instant.now() else null
            return NotificationUpdateResponse(
                notifications.updateStatusForRecipient(userId, request.status, readAt),
            )
        }

        val items = notifications.findAllByIdInAndRecipientId(requireNotNull(request.ids), userId)
        items.forEach { it.setStatus(request.status) }
        notifications.saveAll(items)
        return NotificationUpdateResponse(items.size)
    }

    @Transactional(readOnly = true)
    fun unreadCount(userId: Int): Long =
        notifications.countByRecipientIdAndStatus(userId, NotificationStatus.UNREAD)

    private fun buildMessage(type: NotificationType, payload: Map<String, Any?>): String {
        val requestId = payload["requestId"]?.toString()
        val resource = payload["resource"]?.toString()
        return when (type) {
            NotificationType.SUBMITTED -> "Request ${requestId ?: "unknown"} has been submitted."
            NotificationType.QUERIED -> "Request ${requestId ?: "unknown"} requires additional information."
            NotificationType.APPROVED -> "Request ${requestId ?: "unknown"} was approved."
            NotificationType.WAITLISTED -> "Request ${requestId ?: "unknown"} was added to the waitlist."
            NotificationType.WAITLIST_FULFILLABLE -> "Waitlisted items for request ${requestId ?: "unknown"} are ready for fulfilment."
            NotificationType.BACKORDER_OVERDUE -> "The expected fulfillment date has passed for line ${payload["lineItemId"] ?: "unknown"} on request ${requestId ?: "unknown"}."
            NotificationType.BACKORDER_DATE_UPDATED -> "The expected fulfillment date was updated for line ${payload["lineItemId"] ?: "unknown"} on request ${requestId ?: "unknown"}."
            NotificationType.REJECTED -> "Request ${requestId ?: "unknown"} was rejected."
            NotificationType.PARTIALLY_APPROVED -> "Request ${requestId ?: "unknown"} was partially approved."
            NotificationType.RESUBMITTED -> "Request ${requestId ?: "unknown"} was resubmitted."
            NotificationType.OVERDUE_RETURN -> "An overdue return needs attention${if (resource != null) " for $resource" else ""}."
            NotificationType.PROCUREMENT -> "Procurement activity was recorded${if (requestId != null) " for request $requestId" else ""}."
            NotificationType.REASSIGNMENT_REQUIRED -> payload["message"]?.toString()
                ?: "Request ${requestId ?: "unknown"} needs manager reassignment."
            NotificationType.REASSIGNED -> "Request ${requestId ?: "unknown"} has a new assigned manager."
            NotificationType.CANCELLED -> "Request ${requestId ?: "unknown"} was cancelled."
            NotificationType.COMPLETED -> "Request ${requestId ?: "unknown"} has been fulfilled."
        }
    }

    fun streamForUser(userId: Int): SseEmitter {
        val emitter = SseEmitter(0L)
        Thread {
            try {
                var lastUnread = unreadCount(userId)
                emitter.send(SseEmitter.event().name("unread-count").data(lastUnread))
                while (true) {
                    Thread.sleep(5000)
                    val current = unreadCount(userId)
                    if (current != lastUnread) {
                        emitter.send(SseEmitter.event().name("unread-count").data(current))
                        lastUnread = current
                    }
                }
            } catch (_: Exception) {
                emitter.complete()
            }
        }.start()
        emitter.onCompletion { /* allow thread to exit via IOException */ }
        emitter.onTimeout { emitter.complete() }
        return emitter
    }

    private fun Notification.toResponse() = NotificationResponse(
        id = id,
        recipientId = recipientId,
        type = type,
        requestId = requestId,
        message = message,
        status = status,
        readAt = readAt,
        createdAt = createdAt,
    )
}
