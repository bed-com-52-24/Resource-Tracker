package com.resourcetracker.notification

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Notification read state. Use READ to mark a notification as seen or UNREAD to return it to the inbox.")
enum class NotificationStatus {
    UNREAD,
    READ,
}
