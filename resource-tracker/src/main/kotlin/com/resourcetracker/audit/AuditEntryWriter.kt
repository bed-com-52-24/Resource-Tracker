package com.resourcetracker.audit

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class AuditEntryWriter(private val jdbc: JdbcTemplate) {
    fun insert(record: AuditRecord, correlationId: String, previousState: String?, newState: String?, changedFields: String?) {
        jdbc.update(
            """
            INSERT INTO audit_entry (
                occurred_at, actor_id, actor_role, action, item_type, item_id, previous_state, new_state,
                comment, ip_address, user_agent, changed_fields, correlation_id
            ) VALUES (?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), ?, ?, ?, CAST(? AS JSON), ?, ?)
            """.trimIndent(),
            java.time.Instant.now(),
            record.actorId,
            record.actorRole,
            record.action.name,
            record.itemType,
            record.itemId,
            previousState,
            newState,
            record.comment,
            record.ipAddress,
            record.userAgent,
            changedFields,
            correlationId,
        )
    }
}
