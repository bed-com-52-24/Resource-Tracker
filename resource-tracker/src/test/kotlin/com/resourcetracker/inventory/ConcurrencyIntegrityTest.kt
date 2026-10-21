package com.resourcetracker.inventory

import com.resourcetracker.approval.ApprovalDecision
import com.resourcetracker.approval.ApprovalService
import com.resourcetracker.approval.DecisionRequest
import com.resourcetracker.department.Department
import com.resourcetracker.department.DepartmentRepository
import com.resourcetracker.resource.Resource
import com.resourcetracker.resource.ResourceCategory
import com.resourcetracker.resource.ResourceRepository
import com.resourcetracker.resource.ResourceType
import com.resourcetracker.security.AuthenticatedUser
import com.resourcetracker.user.Role
import com.resourcetracker.user.User
import com.resourcetracker.user.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(
    properties = [
        "app.security.jwt-secret=resource-tracker-concurrency-tests-secret-32",
        "spring.jpa.show-sql=false",
    ],
)
@Testcontainers(disabledWithoutDocker = true)
class ConcurrencyIntegrityTest {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var departments: DepartmentRepository

    @Autowired
    private lateinit var resources: ResourceRepository

    @Autowired
    private lateinit var inventories: InventoryRepository

    @Autowired
    private lateinit var users: UserRepository

    @Autowired
    private lateinit var inventoryService: InventoryService

    @Autowired
    private lateinit var approvalService: ApprovalService

    private lateinit var department: Department
    private lateinit var resource: Resource
    private var inventoryId: Long? = null
    private var approvalRequestId: Int? = null
    private var managerId: Int? = null
    private var teamId: Int? = null
    private var projectId: Int? = null
    private var vendorId: Int? = null

    private val caller = AuthenticatedUser(
        id = Int.MAX_VALUE,
        username = "concurrency-test",
        role = Role.ADMIN,
        jti = "concurrency-test",
        tokenExpiresAt = java.time.Instant.MAX,
    )

    companion object {
        @Container
        @JvmStatic
        val mysql = MySQLContainer<Nothing>("mysql:8.4").apply {
            withDatabaseName("resource_tracker")
            withUsername("test")
            withPassword("test")
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

    @BeforeEach
    fun createInventoryFixture() {
        val suffix = UUID.randomUUID().toString()
        department = departments.save(Department(name = "Concurrency $suffix"))
        resource = resources.save(
            Resource(
                code = "C-${suffix.take(16)}",
                name = "Concurrency test resource",
                type = ResourceType.INTERNAL,
                category = ResourceCategory.EQUIPMENT,
                unitOfMeasure = "unit",
                department = department,
            ),
        )
        val inventory = inventories.save(
            Inventory(
                resource = resource,
                location = "TEST",
                totalQuantity = 1,
            ),
        )
        inventoryId = inventory.id
    }

    @AfterEach
    fun checkInvariantsAndCleanUp() {
        try {
            val invalidRows = jdbc.queryForList(
                """
                SELECT id
                FROM resource_inventory
                WHERE total_quantity < 0
                   OR reserved_quantity < 0
                   OR issued_quantity < 0
                   OR reserved_quantity + issued_quantity > total_quantity
                   OR total_quantity - reserved_quantity - issued_quantity < 0
                """.trimIndent(),
            )
            assertTrue(invalidRows.isEmpty(), "Inventory invariant violations: $invalidRows")

            inventoryId?.let { id ->
                val quantities = jdbc.queryForMap(
                    """
                    SELECT total_quantity, reserved_quantity, issued_quantity,
                           total_quantity - reserved_quantity - issued_quantity AS available_quantity
                    FROM resource_inventory WHERE id = ?
                    """.trimIndent(),
                    id,
                )
                val total = (quantities["total_quantity"] as Number).toInt()
                val reserved = (quantities["reserved_quantity"] as Number).toInt()
                val issued = (quantities["issued_quantity"] as Number).toInt()
                val available = (quantities["available_quantity"] as Number).toInt()
                assertEquals(total - reserved - issued, available)
            }
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS fail_inventory_audit")
            approvalRequestId?.let { id ->
                jdbc.update("DELETE FROM audit_logs WHERE request_id = ?", id)
                jdbc.update("DELETE FROM request_line_items WHERE request_id = ?", id)
                jdbc.update("DELETE FROM resource_request_line_items WHERE request_id = ?", id)
                jdbc.update("DELETE FROM resource_requests WHERE request_id = ?", id)
            }
            managerId?.let { id ->
                jdbc.update("DELETE FROM user_teams WHERE user_id = ?", id)
                jdbc.update("DELETE FROM users WHERE user_id = ?", id)
            }
            teamId?.let { jdbc.update("DELETE FROM teams WHERE team_id = ?", it) }
            projectId?.let { jdbc.update("DELETE FROM projects WHERE project_id = ?", it) }
            vendorId?.let { jdbc.update("DELETE FROM vendors WHERE vendor_id = ?", it) }
            inventoryId?.let { id ->
                jdbc.update("DELETE FROM inventory_audit_logs WHERE inventory_id = ?", id)
                jdbc.update("DELETE FROM resource_inventory WHERE id = ?", id)
            }
            resources.delete(resource)
            departments.delete(department)
        }
    }

    @Test
    fun `parallel reservations cannot reserve the last unit twice`() {
        val outcomes = runTogether(
            List(2) {
                {
                    try {
                        inventoryService.transact(
                            resource.id,
                            InventoryTransactionRequest(
                                type = InventoryTransactionType.RESERVE,
                                quantity = 1,
                                location = "TEST",
                            ),
                            caller,
                        )
                        true
                    } catch (_: com.resourcetracker.common.BusinessRuleException) {
                        false
                    }
                }
            },
        )

        assertEquals(1, outcomes.count { it })
        assertEquals(1, inventoryQuantity("reserved_quantity"))
        assertEquals(0, inventoryQuantity("total_quantity - reserved_quantity - issued_quantity"))
        assertEquals(1, inventoryAuditCount())
    }

    @Test
    fun `parallel returns restore stock without over-allocation`() {
        setInventoryQuantities(total = 2, reserved = 0, issued = 2)

        val outcomes = runTogether(
            List(2) {
                {
                    inventoryService.transact(
                        resource.id,
                        InventoryTransactionRequest(
                            type = InventoryTransactionType.RETURN,
                            quantity = 1,
                            location = "TEST",
                        ),
                        caller,
                    )
                    true
                }
            },
        )

        assertEquals(listOf(true, true), outcomes)
        assertEquals(0, inventoryQuantity("issued_quantity"))
        assertEquals(2, inventoryQuantity("total_quantity - reserved_quantity - issued_quantity"))
        assertEquals(2, inventoryAuditCount())
    }

    @Test
    fun `audit insert failure rolls back reservation`() {
        jdbc.execute(
            """
            CREATE TRIGGER fail_inventory_audit
            BEFORE INSERT ON inventory_audit_logs
            FOR EACH ROW
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced audit failure'
            """.trimIndent(),
        )

        org.junit.jupiter.api.Assertions.assertThrows(DataAccessException::class.java) {
            inventoryService.transact(
                resource.id,
                InventoryTransactionRequest(
                    type = InventoryTransactionType.RESERVE,
                    quantity = 1,
                    location = "TEST",
                ),
                caller,
            )
        }

        assertEquals(0, inventoryQuantity("reserved_quantity"))
        assertEquals(1, inventoryQuantity("total_quantity"))
        assertEquals(0, inventoryAuditCount())
    }

    @Test
    fun `parallel approve and reject produce one decision and one audit`() {
        val requestId = createApprovalFixture()
        val outcomes = runTogether(
            listOf(
                {
                    decide(requestId, DecisionRequest(ApprovalDecision.APPROVE))
                },
                {
                    decide(requestId, DecisionRequest(ApprovalDecision.REJECT, "Not required"))
                },
            ),
        )

        assertEquals(1, outcomes.count { it })
        assertEquals(1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE request_id = ?",
            Int::class.java,
            requestId,
        ))
        val finalStatus = jdbc.queryForObject(
            "SELECT status FROM resource_requests WHERE request_id = ?",
            String::class.java,
            requestId,
        )
        assertTrue(finalStatus == "APPROVED" || finalStatus == "REJECTED")
    }

    private fun decide(requestId: Int, request: DecisionRequest): Boolean =
        try {
            approvalService.decide(requestId, requireNotNull(managerId), request)
            true
        } catch (exception: ResponseStatusException) {
            assertEquals(HttpStatus.CONFLICT, exception.statusCode)
            false
        }

    private fun createApprovalFixture(): Int {
        val manager = users.save(
            User(
                username = "manager-${UUID.randomUUID()}",
                email = "${UUID.randomUUID()}@example.test",
                fullName = "Concurrency Test Manager",
                passwordHash = "not-used",
                role = Role.DEPARTMENT_MANAGER,
                department = department,
            ),
        )
        managerId = manager.id

        jdbc.update(
            "INSERT INTO vendors (vendor_name, status) VALUES (?, 'ACTIVE')",
            "Concurrency test vendor",
        )
        vendorId = jdbc.queryForObject(
            "SELECT vendor_id FROM vendors WHERE vendor_name = ?",
            Int::class.java,
            "Concurrency test vendor",
        )

        val suffix = UUID.randomUUID().toString()
        jdbc.update(
            """
            INSERT INTO projects (project_name, department_id, status, id, code, start_date)
            VALUES (?, ?, 'ACTIVE', ?, ?, CURRENT_DATE)
            """.trimIndent(),
            "Concurrency project $suffix",
            department.id,
            suffix,
            "P-${suffix.take(16)}",
        )
        projectId = jdbc.queryForObject(
            "SELECT project_id FROM projects WHERE code = ?",
            Int::class.java,
            "P-${suffix.take(16)}",
        )
        jdbc.update(
            "INSERT INTO teams (team_name, department_id, project_id) VALUES (?, ?, ?)",
            "Concurrency team $suffix",
            department.id,
            projectId,
        )
        teamId = jdbc.queryForObject(
            "SELECT team_id FROM teams WHERE team_name = ?",
            Int::class.java,
            "Concurrency team $suffix",
        )

        val requestNumber = "T-${suffix.take(16)}"
        jdbc.update(
            """
            INSERT INTO resource_requests (
                item_description, manufacturer, unit_price, quantity, vendor_id,
                department_id, project_id, team_id, requester_id, assigned_manager_id,
                status, request_number, total_amount
            ) VALUES ('Test item', 'Test manufacturer', 1.00, 1, ?, ?, ?, ?, ?, ?, 'PENDING', ?, 1.00)
            """.trimIndent(),
            vendorId,
            department.id,
            projectId,
            teamId,
            manager.id,
            manager.id,
            requestNumber,
        )
        val requestId = jdbc.queryForObject(
            "SELECT request_id FROM resource_requests WHERE request_number = ?",
            Int::class.java,
            requestNumber,
        )
        approvalRequestId = requestId
        return requireNotNull(requestId)
    }

    private fun setInventoryQuantities(total: Int, reserved: Int, issued: Int) {
        jdbc.update(
            """
            UPDATE resource_inventory
            SET total_quantity = ?, reserved_quantity = ?, issued_quantity = ?
            WHERE id = ?
            """.trimIndent(),
            total,
            reserved,
            issued,
            inventoryId,
        )
    }

    private fun inventoryQuantity(expression: String): Int =
        jdbc.queryForObject(
            "SELECT $expression FROM resource_inventory WHERE id = ?",
            Int::class.java,
            inventoryId,
        ) ?: error("Inventory row not found")

    private fun inventoryAuditCount(): Int =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM inventory_audit_logs WHERE inventory_id = ?",
            Int::class.java,
            inventoryId,
        ) ?: 0

    private fun <T> runTogether(operations: List<() -> T>): List<T> {
        val ready = CountDownLatch(operations.size)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(operations.size)
        try {
            val futures = operations.map { operation ->
                executor.submit(Callable {
                    ready.countDown()
                    check(start.await(10, TimeUnit.SECONDS)) { "Timed out waiting for concurrent start" }
                    operation()
                })
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS), "Concurrent workers did not become ready")
            start.countDown()
            return futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            start.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "Concurrent workers did not stop")
        }
    }
}
