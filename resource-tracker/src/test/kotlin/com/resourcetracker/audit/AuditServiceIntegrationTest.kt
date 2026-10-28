package com.resourcetracker.audit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@SpringBootTest(properties = ["app.security.jwt-secret=resource-tracker-audit-test-secret-32-characters"])
@Testcontainers(disabledWithoutDocker = true)
class AuditServiceIntegrationTest {
    @Autowired
    private lateinit var audit: AuditService

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    companion object {
          @Container
          @JvmStatic
          val mysql = MySQLContainer<Nothing>("mysql:8.4").apply {
                withDatabaseName("resource_tracker")
                withUsername("test")
                withPassword("test")
                withCommand("--log-bin-trust-function-creators=1")
}

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
            registry.add("spring.datasource.driver-class-name") { "com.mysql.cj.jdbc.Driver" }
        }
    }

    @Test
    fun `record requires a caller transaction`() {
        assertThrows(IllegalTransactionStateException::class.java) {
            audit.record(record())
        }
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
            "SELECT previous_state, new_state FROM audit_entry WHERE item_id = ?",
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
