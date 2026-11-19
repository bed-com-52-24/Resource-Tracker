package com.resourcetracker.notification

import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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
        val requestNumber = payload["requestNumber"]?.toString()?.takeIf { it.isNotBlank() }
        val request = requestNumber?.let { "request $it" } ?: "this request"
        val reference = requestNumber?.let { " (reference: $it)" }.orEmpty()
        val resource = payload["resource"]?.toString()?.takeIf { it.isNotBlank() }
        val dueDate = friendlyDate(payload["expectedReturnDate"])
        val requestedDate = friendlyDate(payload["requestedReturnDate"], "a later date")
        return when (type) {
            NotificationType.SUBMITTED -> "A new resource request$reference is ready for your review."
            NotificationType.QUERIED -> "The manager has a question about $request. Open it to see what information is needed."
            NotificationType.APPROVED -> "Good news: $request has been approved."
            NotificationType.WAITLISTED -> "Some items in $request are not available yet. We’ll let you know when they’re ready."
            NotificationType.WAITLIST_FULFILLABLE -> "Items from $request are now available and ready to be issued."
            NotificationType.BACKORDER_OVERDUE -> "Some items from $request are taking longer than expected. Open it to see the latest details."
            NotificationType.BACKORDER_DATE_UPDATED -> "The expected date for items from $request has changed to ${friendlyDate(payload["expectedFulfillmentDate"], "a later date")}."
            NotificationType.REJECTED -> "$request was not approved. Open it to read the manager’s comments."
            NotificationType.PARTIALLY_APPROVED -> "Some items or quantities in $request were approved. Open it to see what was approved."
            NotificationType.RESUBMITTED -> "An updated resource request$reference is ready for your review."
            NotificationType.OVERDUE_RETURN -> "${resource?.let { "The borrowed item “$it”" } ?: "A borrowed item"} is overdue. Please arrange its return as soon as possible."
            NotificationType.RETURN_DUE_7_DAYS -> "A borrowed item from $request is due in 7 days, on $dueDate."
            NotificationType.RETURN_DUE_2_DAYS -> "A borrowed item from $request is due in 2 days, on $dueDate."
            NotificationType.RETURN_DUE_TODAY -> "A borrowed item from $request is due today ($dueDate). Please arrange its return."
            NotificationType.RETURN_EXTENSION_REQUESTED -> "A team member has asked to keep the borrowed items from $request until $requestedDate. Please review the request."
            NotificationType.RETURN_EXTENSION_APPROVED -> "The return date for items from $request has been extended to $requestedDate."
            NotificationType.RETURN_EXTENSION_REJECTED -> "The request to extend the return date for $request was not approved. Please follow the current due date."
            NotificationType.PROCUREMENT -> "There’s an update on a purchase for $request. Open it to see the latest details."
            NotificationType.RESOURCE_ISSUED -> "Items from $request have been handed over to your team. Open the request to see the quantities."
            NotificationType.BUDGET_OVERRIDE_REQUIRED -> "A resource request$reference is above the department’s available budget. Please review whether extra budget can be approved. The purchase is on hold until you decide."
            NotificationType.BUDGET_OVERRIDE_DECIDED -> if (payload["decision"]?.toString() == "APPROVED") {
                "The request for extra budget for $request was approved. The purchase can now proceed."
            } else {
                "The request for extra budget for $request was not approved, so the purchase cannot proceed. Contact your manager if you have questions."
            }
            NotificationType.REASSIGNMENT_REQUIRED -> "A resource request$reference needs a department manager before it can be reviewed. Please assign a manager."
            NotificationType.REASSIGNED -> "A new manager has been assigned to $request."
            NotificationType.CANCELLED -> "The requester has cancelled $request."
            NotificationType.COMPLETED -> "Your request$reference has been completed."
        }
    }

    private fun friendlyDate(value: Any?, fallback: String = "a date not available"): String {
        val date = when (value) {
            is LocalDate -> value
            null -> null
            else -> runCatching { LocalDate.parse(value.toString()) }.getOrNull()
        }
        return date?.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
            ?: fallback
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
