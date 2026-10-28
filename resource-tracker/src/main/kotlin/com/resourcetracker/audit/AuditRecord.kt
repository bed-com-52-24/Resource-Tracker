package com.resourcetracker.audit

import com.resourcetracker.security.AuthenticatedUser
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

data class AuditRecord(
    val actorId: Int? = null,
    val actorRole: String? = null,
    val action: AuditAction,
    val itemType: String,
    val itemId: String,
    val previousState: Any? = null,
    val newState: Any? = null,
    val comment: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val changedFields: Any? = null,
    val correlationId: String? = null,
) {
    init {
        require(itemType.isNotBlank() && itemType.length <= 64) {
            "itemType must contain 1 to 64 characters"
        }
        require(itemId.isNotBlank() && itemId.length <= 128) {
            "itemId must contain 1 to 128 characters"
        }
        require(comment == null || comment.length <= 500) {
            "comment must not exceed 500 characters"
        }
        require(actorRole == null || actorRole.length <= 64) {
            "actorRole must not exceed 64 characters"
        }
        require(ipAddress == null || ipAddress.length <= 45) {
            "ipAddress must not exceed 45 characters"
        }
        require(userAgent == null || userAgent.length <= 512) {
            "userAgent must not exceed 512 characters"
        }
        require(correlationId == null || AuditCorrelationContext.isValid(correlationId)) {
            "correlationId has an invalid format"
        }
    }

    companion object {
        fun currentActor(
            action: AuditAction,
            itemType: String,
            itemId: String,
            previousState: Any? = null,
            newState: Any? = null,
            comment: String? = null,
            changedFields: Any? = null,
        ): AuditRecord {
            val principal = SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedUser
            val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
            return AuditRecord(
                actorId = principal?.id,
                actorRole = principal?.role?.name,
                action = action,
                itemType = itemType,
                itemId = itemId,
                previousState = previousState,
                newState = newState,
                comment = comment,
                ipAddress = request?.remoteAddr,
                userAgent = request?.getHeader("User-Agent")?.take(512),
                changedFields = changedFields,
                correlationId = AuditCorrelationContext.currentOrNew(),
            )
        }
    }
}
