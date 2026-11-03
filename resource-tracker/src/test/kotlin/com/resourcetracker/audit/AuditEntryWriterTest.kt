package com.resourcetracker.audit

import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate

class AuditEntryWriterTest {
    @Test
    fun `insert stores plain text metadata and JSON snapshots in their columns`() {
        val dataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:audit-entry-writer;MODE=MySQL;DB_CLOSE_DELAY=-1")
        }
        val jdbc = JdbcTemplate(dataSource)
        jdbc.execute(
            """
            CREATE TABLE audit_entry (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                occurred_at TIMESTAMP(6) NOT NULL,
                actor_id INT,
                actor_role VARCHAR(64),
                action VARCHAR(48) NOT NULL,
                item_type VARCHAR(64) NOT NULL,
                item_id VARCHAR(128) NOT NULL,
                previous_state JSON,
                new_state JSON,
                comment VARCHAR(500),
                ip_address VARCHAR(45),
                user_agent VARCHAR(512),
                changed_fields JSON,
                correlation_id VARCHAR(64) NOT NULL
            )
            """.trimIndent(),
        )

        AuditEntryWriter(jdbc).insert(
            record = AuditRecord(
                action = AuditAction.AUTH_LOGIN,
                itemType = "AUTHENTICATION",
                itemId = "member@example.test",
                userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
            ),
            correlationId = "correlation-id",
            previousState = """{"status":"PENDING"}""",
            newState = """{"status":"APPROVED"}""",
            changedFields = """["status"]""",
        )

        val row = jdbc.queryForMap(
            "SELECT item_id, user_agent, CAST(previous_state AS VARCHAR) AS previous_state, " +
                "CAST(new_state AS VARCHAR) AS new_state, " +
                "CAST(changed_fields AS VARCHAR) AS changed_fields FROM audit_entry",
        )
        assertEquals("member@example.test", row["item_id"])
        assertEquals("Mozilla/5.0 (Windows NT 10.0; Win64; x64)", row["user_agent"])
        assertTrue(row["previous_state"].toString().contains("PENDING"))
        assertTrue(row["new_state"].toString().contains("APPROVED"))
        assertTrue(row["changed_fields"].toString().contains("status"))
    }
}
