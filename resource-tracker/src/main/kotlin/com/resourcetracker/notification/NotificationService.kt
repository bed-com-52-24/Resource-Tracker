package com.resourcetracker.notification

import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationService(
    private val notifications: NotificationRepository,
) {

    @Transactional(propagation = Propagation.MANDATORY)
    fun notify(userIds: Collection<Int>, type: NotificationType, payload: Map<String, Any?>) {
        val requestId = payload["requestId"]?.toString()
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
    fun listForUser(userId: Int, unreadOnly: Boolean = false, search: String?, pageable: Pageable): List<NotificationResponse> {
        val normalized = search?.trim()?.takeIf { it.isNotEmpty() }
        val page = when {
            normalized != null -> notifications.searchForUser(userId, unreadOnly, normalized, pageable)
            unreadOnly -> notifications.findByRecipientIdAndReadOrderByCreatedAtDesc(userId, false, pageable)
            else -> notifications.findByRecipientIdOrderByCreatedAtDesc(userId, pageable)
        }
        return page.content.map { it.toResponse() }
    }

    @Transactional
    fun markRead(userId: Int, notificationId: Long): NotificationResponse {
        val notification = notifications.findByIdAndRecipientId(notificationId, userId)
            ?: throw IllegalArgumentException("Notification not found for the current user")
        notification.read = true
        return notifications.save(notification).toResponse()
    }

    @Transactional
    fun markAllRead(userId: Int) {
        val unread = notifications.findByRecipientIdAndReadOrderByCreatedAtDesc(userId, false, Pageable.unpaged())
        unread.content.forEach { it.read = true }
        notifications.saveAll(unread.content)
    }

    @Transactional(readOnly = true)
    fun unreadCount(userId: Int): Long = notifications.countByRecipientIdAndRead(userId, false)

    private fun buildMessage(type: NotificationType, payload: Map<String, Any?>): String {
        val requestId = payload["requestId"]?.toString()
        val resource = payload["resource"]?.toString()
        val item = payload["item"]?.toString()
        return when (type) {
            NotificationType.SUBMITTED -> "Request ${requestId ?: "unknown"} has been submitted."
            NotificationType.QUERIED -> "Request ${requestId ?: "unknown"} requires additional information."
            NotificationType.APPROVED -> "Request ${requestId ?: "unknown"} was approved."
            NotificationType.REJECTED -> "Request ${requestId ?: "unknown"} was rejected."
            NotificationType.PARTIALLY_APPROVED -> "Request ${requestId ?: "unknown"} was partially approved."
            NotificationType.RESUBMITTED -> "Request ${requestId ?: "unknown"} was resubmitted."
            NotificationType.WAITLIST_FULFILLABLE -> "A waitlist item is now fulfillable${if (resource != null) " for $resource" else ""}."
            NotificationType.OVERDUE_RETURN -> "An overdue return needs attention${if (resource != null) " for $resource" else ""}."
            NotificationType.PROCUREMENT -> "Procurement activity was recorded${if (requestId != null) " for request $requestId" else ""}."
            NotificationType.LOW_STOCK -> "Low stock alert${if (item != null) " for $item" else ""}."
        }
    }

    private fun Notification.toResponse() = NotificationResponse(
        id = id,
        recipientId = recipientId,
        type = type,
        requestId = requestId,
        message = message,
        read = read,
        createdAt = createdAt,
    )
}
