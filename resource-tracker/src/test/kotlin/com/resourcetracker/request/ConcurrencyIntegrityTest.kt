package com.resourcetracker.request

import com.resourcetracker.department.Department
import com.resourcetracker.department.DepartmentRepository
import com.resourcetracker.project.Project
import com.resourcetracker.project.ProjectRepository
import com.resourcetracker.request.dto.Decision
import com.resourcetracker.request.dto.RequestDecisionDto
import com.resourcetracker.request.entity.FulfillmentPreference
import com.resourcetracker.request.entity.RequestCategory
import com.resourcetracker.request.entity.RequestStatus
import com.resourcetracker.request.entity.ResourceRequest
import com.resourcetracker.request.entity.ResourceRequestLineItem
import com.resourcetracker.request.entity.ReturnCondition
import com.resourcetracker.request.entity.StockIssue
import com.resourcetracker.request.entity.StockReturn
import com.resourcetracker.request.repository.RequestLineItemRepository
import com.resourcetracker.request.repository.ResourceRequestRepository
import com.resourcetracker.request.repository.StockIssueRepository
import com.resourcetracker.request.repository.StockReturnRepository
import com.resourcetracker.request.service.CreateReservationRequest
import com.resourcetracker.request.service.CreateReturnRequest
import com.resourcetracker.request.service.ReservationService
import com.resourcetracker.request.service.ResourceRequestService
import com.resourcetracker.request.service.StockIssueRequest
import com.resourcetracker.request.service.StockOperationsService
import com.resourcetracker.request.service.VerifyReturnRequest
import com.resourcetracker.common.UserReference
import com.resourcetracker.resource.Resource
import com.resourcetracker.resource.ResourceCategory
import com.resourcetracker.resource.ResourceRepository
import com.resourcetracker.resource.ResourceType
import com.resourcetracker.team.Team
import com.resourcetracker.team.TeamRepository
import com.resourcetracker.user.Role
import com.resourcetracker.user.User
import com.resourcetracker.user.UserRepository
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataAccessException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@SpringBootTest(
    properties = [
        "app.security.jwt-secret=resource-tracker-concurrency-tests-secret-32",
        "spring.jpa.show-sql=false",
    ],
)
class ConcurrencyIntegrityTest {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var departments: DepartmentRepository

    @Autowired
    private lateinit var projects: ProjectRepository

    @Autowired
    private lateinit var teams: TeamRepository

    @Autowired
    private lateinit var users: UserRepository

    @Autowired
    private lateinit var resources: ResourceRepository

    @Autowired
    private lateinit var requests: ResourceRequestRepository

    @Autowired
    private lateinit var lines: RequestLineItemRepository

    @Autowired
    private lateinit var issues: StockIssueRepository

    @Autowired
    private lateinit var returns: StockReturnRepository

    @Autowired
    private lateinit var reservations: ReservationService

    @Autowired
    private lateinit var requestService: ResourceRequestService

    @Autowired
    private lateinit var stockOperations: StockOperationsService

    private lateinit var department: Department
    private lateinit var project: Project
    private lateinit var team: Team
    private lateinit var manager: User
    private lateinit var resource: Resource
    private lateinit var request: ResourceRequest
    private lateinit var line: ResourceRequestLineItem

    @BeforeEach
    fun createFixture() {
        val suffix = UUID.randomUUID().toString()
        department = departments.save(Department(name = "Concurrency $suffix"))
        manager = users.save(
            User(
                username = "manager-${suffix.take(12)}",
                email = "$suffix@example.test",
                role = Role.DEPARTMENT_MANAGER,
                department = department,
            ),
        )
        project = projects.save(
            Project(
                name = "Concurrency $suffix",
                code = "C-${suffix.take(12)}",
                department = department,
                startDate = LocalDate.now(),
            ),
        )
        team = teams.save(
            Team(
                name = "Concurrency $suffix",
                department = department,
                manager = manager,
                projects = mutableSetOf(project),
            ),
        )
        resource = Resource(
            code = "C-${suffix.take(12)}",
            name = "Concurrency test resource",
            type = ResourceType.INTERNAL,
            category = ResourceCategory.EQUIPMENT,
            unitOfMeasure = "unit",
            quantity = 1,
            department = department,
        ).also { it.create() }.let(resources::save)
        request = requests.save(
            ResourceRequest(
                requestNumber = "C-${suffix.take(16)}",
                itemDescription = resource.name,
                manufacturer = "Test",
                unitPrice = BigDecimal.ONE,
                quantity = 1,
                departmentId = department.id,
                projectId = project.id,
                teamId = team.id,
                requesterId = manager.id,
                assignedManagerId = manager.id,
                requiredDate = LocalDate.now(),
                uniqueKey = suffix,
                status = RequestStatus.APPROVED,
                justification = "Concurrency test",
                totalAmount = BigDecimal.ONE,
            ),
        )
        line = lines.save(
            ResourceRequestLineItem(
                requestId = requireNotNull(request.requestId),
                description = resource.name,
                manufacturer = "Test",
                unitPrice = BigDecimal.ONE,
                quantity = 1,
                category = RequestCategory.OTHER,
                resourceId = resource.id,
                fulfillmentPreference = FulfillmentPreference.INTERNAL.name,
                unitOfMeasure = "unit",
                approvedQuantity = 1,
                requestedDurationDays = 30,
                approvedDurationDays = 30,
            ),
        )
    }

    @Test
    fun `issuing stock response identifies the request receiver`() {
        val response = stockOperations.issue(
            requireNotNull(request.requestId),
            manager.id,
            StockIssueRequest(requireNotNull(line.lineItemId), 1),
        )

        assertEquals(UserReference(manager.id, manager.username), response.receiver)
    }

    @AfterEach
    fun cleanUp() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_stock_movement")
        request.requestId?.let { requestId ->
            jdbc.update("DELETE FROM notifications WHERE request_id = ?", requestId)
            jdbc.update("DELETE FROM stock_movements WHERE request_id = ?", requestId)
            jdbc.update(
                "DELETE FROM stock_returns WHERE issue_id IN (SELECT issue_id FROM stock_issues WHERE request_id = ?)",
                requestId,
            )
            jdbc.update("DELETE FROM stock_issues WHERE request_id = ?", requestId)
            jdbc.update("DELETE FROM stock_reservations WHERE request_id = ?", requestId)
            jdbc.update("DELETE FROM request_history WHERE request_id = ?", requestId)
            jdbc.update("DELETE FROM resource_request_line_items WHERE request_id = ?", requestId)
            jdbc.update("DELETE FROM resource_requests WHERE request_id = ?", requestId)
        }
        jdbc.update("DELETE FROM team_projects WHERE team_id = ?", team.id)
        jdbc.update("DELETE FROM user_teams WHERE user_id = ?", manager.id)
        jdbc.update("DELETE FROM teams WHERE team_id = ?", team.id)
        jdbc.update("DELETE FROM projects WHERE project_id = ?", project.id)
        jdbc.update("DELETE FROM users WHERE user_id = ?", manager.id)
        jdbc.update("DELETE FROM resources WHERE id = ?", resource.id)
        jdbc.update("DELETE FROM departments WHERE department_id = ?", department.id)
    }

    @Test
    fun `parallel reservations cannot allocate the last unit twice`() {
        val requestId = requireNotNull(request.requestId)
        val lineId = requireNotNull(line.lineItemId)
        val outcomes = runTogether(
            List(2) {
                {
                    try {
                        reservations.reserve(requestId, manager.id, CreateReservationRequest(lineId, 1))
                        true
                    } catch (exception: ResponseStatusException) {
                        assertEquals(HttpStatus.CONFLICT, exception.statusCode)
                        false
                    }
                }
            },
        )

        assertEquals(1, outcomes.count { it })
        assertEquals(1, jdbc.queryForObject(
            "SELECT COALESCE(SUM(quantity), 0) FROM stock_reservations WHERE resource_id = ? AND status = 'ACTIVE'",
            Int::class.java,
            resource.id,
        ))
        assertEquals(1, jdbc.queryForObject("SELECT quantity FROM resources WHERE id = ?", Int::class.java, resource.id))
    }

    @Test
    fun `parallel returns restore stock without over-allocation`() {
        resource.quantity = 0
        resources.save(resource)
        val issue = issues.save(
            StockIssue(
                requestId = requireNotNull(request.requestId),
                lineItemId = requireNotNull(line.lineItemId),
                resourceId = resource.id,
                teamId = team.id,
                quantity = 2,
                issuedBy = manager.id,
            ),
        )
        val outcomes = runTogether(
            List(2) {
                {
                    try {
                        stockOperations.requestReturn(issue.id, manager.id, CreateReturnRequest(1))
                        true
                    } catch (exception: ResponseStatusException) {
                        assertEquals(HttpStatus.CONFLICT, exception.statusCode)
                        false
                    }
                }
            },
        )
        assertEquals(listOf(true, true), outcomes)
        val returnIds = jdbc.queryForList(
            "SELECT return_id FROM stock_returns WHERE issue_id = ? AND status = 'PENDING'",
            Int::class.java,
            issue.id,
        )

        returnIds.forEach { returnId ->
            stockOperations.verifyReturn(
                requireNotNull(returnId),
                manager.id,
                VerifyReturnRequest(ReturnCondition.USABLE),
            )
        }

        assertEquals(2, jdbc.queryForObject("SELECT quantity FROM resources WHERE id = ?", Int::class.java, resource.id))
        assertEquals(2, jdbc.queryForObject("SELECT returned_quantity FROM stock_issues WHERE issue_id = ?", Int::class.java, issue.id))
    }

    @Test
    fun `parallel approve and reject produce one decision`() {
        request.status = RequestStatus.PENDING
        requests.saveAndFlush(request)
        val requestId = requireNotNull(request.requestId)
        val outcomes = runTogether(
            listOf(
                {
                    decide(requestId, RequestDecisionDto(Decision.APPROVE))
                },
                {
                    decide(requestId, RequestDecisionDto(Decision.REJECT, "Not required"))
                },
            ),
        )

        assertEquals(1, outcomes.count { it })
        val finalStatus = jdbc.queryForObject(
            "SELECT status FROM resource_requests WHERE request_id = ?",
            String::class.java,
            requestId,
        )
        assertTrue(finalStatus == RequestStatus.APPROVED.name || finalStatus == RequestStatus.REJECTED.name)
    }

    @Test
    @Disabled("MySQL specific trigger test")
    fun `movement insert failure rolls back return verification`() {
        resource.quantity = 0
        resources.save(resource)
        val issue = issues.save(
            StockIssue(
                requestId = requireNotNull(request.requestId),
                lineItemId = requireNotNull(line.lineItemId),
                resourceId = resource.id,
                teamId = team.id,
                quantity = 1,
                issuedBy = manager.id,
            ),
        )
        val stockReturn = returns.save(
            StockReturn(
                issueId = issue.id,
                quantity = 1,
                returnedBy = manager.id,
            ),
        )
        jdbc.execute(
            """
            CREATE TRIGGER fail_stock_movement
            BEFORE INSERT ON stock_movements
            FOR EACH ROW
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced stock movement failure'
            """.trimIndent(),
        )

        assertThrows(DataAccessException::class.java) {
            stockOperations.verifyReturn(
                stockReturn.id,
                manager.id,
                VerifyReturnRequest(ReturnCondition.USABLE),
            )
        }

        assertEquals("PENDING", jdbc.queryForObject(
            "SELECT status FROM stock_returns WHERE return_id = ?",
            String::class.java,
            stockReturn.id,
        ))
        assertEquals(0, jdbc.queryForObject("SELECT quantity FROM resources WHERE id = ?", Int::class.java, resource.id))
        assertEquals(0, jdbc.queryForObject("SELECT returned_quantity FROM stock_issues WHERE issue_id = ?", Int::class.java, issue.id))
    }

    private fun decide(requestId: Int, command: RequestDecisionDto): Boolean =
        try {
            requestService.decide(requestId, manager, command)
            true
        } catch (exception: ResponseStatusException) {
            assertEquals(HttpStatus.CONFLICT, exception.statusCode)
            false
        } catch (exception: OptimisticLockingFailureException) {
            false
        }

    private fun runTogether(actions: List<() -> Boolean>): List<Boolean> {
        val ready = CountDownLatch(actions.size)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(actions.size)
        return try {
            val futures = actions.map { action ->
                executor.submit(Callable {
                    ready.countDown()
                    check(start.await(10, TimeUnit.SECONDS)) { "Concurrent tasks did not start" }
                    action()
                })
            }
            check(ready.await(10, TimeUnit.SECONDS)) { "Concurrent tasks were not ready" }
            start.countDown()
            futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }
}
