package com.resourcetracker.notification

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(name = "NotificationResponse")
data class NotificationResponse(
    val id: Long,
    val recipientId: Int,
    val type: NotificationType,
    val requestId: String?,
    val message: String,
    val read: Boolean,
    val createdAt: Instant,
)

data class NotificationCountResponse(
    val count: Long,
)
