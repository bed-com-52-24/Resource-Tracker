package com.resourcetracker.audit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@SpringBootTest(properties = ["app.security.jwt-secret=resource-tracker-audit-test-secret-32-characters"])
class AuditServiceIntegrationTest {
    @Autowired
    private lateinit var audit: AuditService

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @BeforeEach
    fun installH2AuditImmutabilityTriggers() {
        jdbc.execute(
            "CREATE TRIGGER IF NOT EXISTS audit_entry_no_update " +
                "BEFORE UPDATE ON audit_entry FOR EACH ROW " +
                "CALL 'com.resourcetracker.testsupport.AuditEntryImmutabilityTrigger'",
        )
        jdbc.execute(
            "CREATE TRIGGER IF NOT EXISTS audit_entry_no_delete " +
                "BEFORE DELETE ON audit_entry FOR EACH ROW " +
                "CALL 'com.resourcetracker.testsupport.AuditEntryImmutabilityTrigger'",
        )
    }



    @Test
    fun `audit insert rolls back with its business transaction`() {
        val departmentName = "audit-rollback-${UUID.randomUUID()}"
        val transaction = TransactionTemplate(transactionManager)

        assertThrows(IllegalStateException::class.java) {
            transaction.executeWithoutResult {
                jdbc.update("INSERT INTO departments (department_name) VALUES (?)", departmentName)
                audit.record(record(itemType = "DEPARTMENT", itemId = departmentName))
                error("force caller rollback")
            }
        }

        assertEquals(
            0,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM departments WHERE department_name = ?",
                Int::class.java,
                departmentName,
            ),
        )
        assertEquals(
            0,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_entry WHERE item_id = ?",
                Int::class.java,
                departmentName,
            ),
        )
    }

    @Test
    fun `audit states redact nested secrets before persistence`() {
        val itemId = UUID.randomUUID().toString()
        TransactionTemplate(transactionManager).executeWithoutResult {
            audit.record(
                record(
                    itemId = itemId,
                    previousState = mapOf("passwordHash" to "old-hash", "name" to "old"),
                    newState = mapOf(
                        "profile" to mapOf("api_key" to "secret-key"),
                        "sessions" to listOf(mapOf("refresh-token" to "secret-token")),
                        "name" to "new",
                    ),
                ),
            )
        }

        val states = jdbc.queryForMap(
            "SELECT CAST(previous_state AS VARCHAR) AS previous_state, " +
                "CAST(new_state AS VARCHAR) AS new_state FROM audit_entry WHERE item_id = ?",
            itemId,
        )
        val previous = states["previous_state"].toString()
        val current = states["new_state"].toString()
        assertFalse(previous.contains("old-hash"))
        assertFalse(current.contains("secret-key"))
        assertFalse(current.contains("secret-token"))
        assertTrue(previous.contains("***"))
        assertTrue(current.contains("***"))
    }

    @Test
    fun `database rejects audit updates and deletes`() {
        val itemId = UUID.randomUUID().toString()
        TransactionTemplate(transactionManager).executeWithoutResult {
            audit.record(record(itemId = itemId))
        }
        val id = jdbc.queryForObject(
            "SELECT id FROM audit_entry WHERE item_id = ?",
            Long::class.java,
            itemId,
        )

        assertThrows(DataAccessException::class.java) {
            jdbc.update("UPDATE audit_entry SET comment = 'changed' WHERE id = ?", id)
        }
        assertThrows(DataAccessException::class.java) {
            jdbc.update("DELETE FROM audit_entry WHERE id = ?", id)
        }
    }

    @Test
    @Disabled("MySQL specific trigger test")
    fun `audit insert failure rolls back the business write`() {
        val departmentName = "audit-failure-${UUID.randomUUID()}"
        jdbc.execute(
            """
            CREATE TRIGGER fail_audit_entry_insert
            BEFORE INSERT ON audit_entry
            FOR EACH ROW
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced audit failure'
            """.trimIndent(),
        )
        try {
            assertThrows(DataAccessException::class.java) {
                TransactionTemplate(transactionManager).executeWithoutResult {
                    jdbc.update("INSERT INTO departments (department_name) VALUES (?)", departmentName)
                    audit.record(record(itemType = "DEPARTMENT", itemId = departmentName))
                }
            }
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS fail_audit_entry_insert")
        }

        assertEquals(
            0,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM departments WHERE department_name = ?",
                Int::class.java,
                departmentName,
            ),
        )
    }

    private fun record(
        itemType: String = "TEST",
        itemId: String = UUID.randomUUID().toString(),
        previousState: Any? = null,
        newState: Any? = null,
    ) = AuditRecord(
        action = AuditAction.REFERENCE_DATA_UPDATED,
        itemType = itemType,
        itemId = itemId,
        previousState = previousState,
        newState = newState,
        correlationId = UUID.randomUUID().toString(),
    )
}
