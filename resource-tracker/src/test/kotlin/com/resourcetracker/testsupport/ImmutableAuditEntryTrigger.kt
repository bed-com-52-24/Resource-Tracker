package com.resourcetracker.testsupport

import org.h2.api.Trigger
import java.sql.Connection

class ImmutableAuditEntryTrigger : Trigger {
    override fun fire(conn: Connection?, oldRow: Array<out Any>?, newRow: Array<out Any>?) {
        throw java.sql.SQLException("Audit entries are immutable")
    }

    override fun init(conn: Connection?, schemaName: String?, triggerName: String?, tableName: String?, before: Boolean, type: Int) = Unit
    override fun close() = Unit
    override fun remove() = Unit
}
