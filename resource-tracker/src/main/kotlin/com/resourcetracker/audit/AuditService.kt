package com.resourcetracker.audit

import com.resourcetracker.security.AuthenticatedUser
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@Service
class AuditService(
    private val writer: AuditEntryWriter,
    private val snapshots: AuditSnapshotSerializer,
) {
    @Transactional(propagation = Propagation.MANDATORY)
    fun record(record: AuditRecord) {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedUser
        val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
        val prepared = record.copy(
            actorId = record.actorId ?: principal?.id,
            actorRole = record.actorRole ?: principal?.role?.name,
            ipAddress = record.ipAddress ?: request?.remoteAddr,
            userAgent = record.userAgent ?: request?.getHeader("User-Agent")?.take(512),
        )
        val correlationId = prepared.correlationId ?: AuditCorrelationContext.currentOrNew()
        writer.insert(
            prepared,
            correlationId,
            snapshots.serialize(prepared.previousState),
            snapshots.serialize(prepared.newState),
            snapshots.serialize(prepared.changedFields),
        )
    }
}
