package com.resourcetracker.notification

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(name = "NotificationResponse")
data class NotificationResponse(
    val id: Int,
    val recipientId: Int,
    val type: NotificationType,
    val requestId: Int?,
    val message: String,
    val status: NotificationStatus,
    val readAt: Instant?,
    val createdAt: Instant,
)

data class NotificationCountResponse(
    val count: Long,
)

data class NotificationUpdateRequest(
    val ids: Set<Int>? = null,
    val all: Boolean = false,
    val status: NotificationStatus,
)

data class NotificationUpdateResponse(
    val updatedCount: Int,
)
