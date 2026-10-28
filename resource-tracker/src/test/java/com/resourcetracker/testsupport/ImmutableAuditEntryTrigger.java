package com.resourcetracker.testsupport;

import org.h2.api.Trigger;

import java.sql.Connection;
import java.sql.SQLException;

public final class ImmutableAuditEntryTrigger implements Trigger {
    @Override
    public void fire(Connection connection, Object[] oldRow, Object[] newRow) throws SQLException {
        throw new SQLException("Audit entries are immutable");
    }
}
