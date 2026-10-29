package com.resourcetracker.audit

import java.time.Instant

data class AuditEntryResponse(
    val id: Long,
    val occurredAt: Instant?,
    val actorId: Int?,
    val actorRole: String?,
    val action: AuditAction,
    val itemType: String,
    val itemId: String,
    val previousState: String?,
    val newState: String?,
    val comment: String?,
    val ipAddress: String?,
    val userAgent: String?,
    val changedFields: String?,
    val correlationId: String,
)

fun AuditEntry.toResponse() = AuditEntryResponse(
    id = id,
    occurredAt = occurredAt,
    actorId = actorId,
    actorRole = actorRole,
    action = action,
    itemType = itemType,
    itemId = itemId,
    previousState = previousState,
    newState = newState,
    comment = comment,
    ipAddress = ipAddress,
    userAgent = userAgent,
    changedFields = changedFields,
    correlationId = correlationId,
)
