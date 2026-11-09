package com.resourcetracker.testsupport

import org.h2.api.Trigger
import java.sql.Connection
import java.sql.SQLException

class AuditEntryImmutabilityTrigger : Trigger {
    override fun fire(connection: Connection, oldRow: Array<Any?>?, newRow: Array<Any?>?) {
        throw SQLException("Audit entries are immutable")
    }
}
