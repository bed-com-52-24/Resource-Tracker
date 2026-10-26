package com.resourcetracker.resource

import com.resourcetracker.department.Department
import com.resourcetracker.request.entity.RequestStatus
import com.resourcetracker.request.repository.ExpectedReturnProjection
import com.resourcetracker.request.repository.ResourceIssueSummary
import com.resourcetracker.request.repository.RequestLineItemRepository
import com.resourcetracker.request.repository.StockIssueRepository
import com.resourcetracker.request.repository.WaitlistLineProjection
import com.resourcetracker.request.service.StockAvailabilityService
import com.resourcetracker.request.service.StockCorrectionRequest
import com.resourcetracker.request.service.StockOperationsService
import com.resourcetracker.security.AuthenticatedUser
import com.resourcetracker.user.Role
import com.resourcetracker.user.User
import com.resourcetracker.user.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Optional

class InventoryReportingServiceTest {
    private val resources = org.mockito.kotlin.mock<ResourceRepository>()
    private val issues = org.mockito.kotlin.mock<StockIssueRepository>()
    private val lines = org.mockito.kotlin.mock<RequestLineItemRepository>()
    private val availability = org.mockito.kotlin.mock<StockAvailabilityService>()
    private val users = org.mockito.kotlin.mock<UserRepository>()
    private val reconciliations = org.mockito.kotlin.mock<ResourceReconciliationRepository>()
    private val stockOperations = org.mockito.kotlin.mock<StockOperationsService>()
    private val clock = Clock.fixed(Instant.parse("2026-10-26T10:00:00Z"), ZoneOffset.UTC)
    private val service = InventoryReportingService(
        resources, issues, lines, availability, users, reconciliations, stockOperations, clock,
    )
    private val pageable = PageRequest.of(0, 20)

    private fun caller(id: Int, role: Role) = AuthenticatedUser(
        id = id,
        username = role.name.lowercase(),
        role = role,
        jti = "test-token",
        tokenExpiresAt = Instant.parse("2026-10-27T00:00:00Z"),
    )

    private fun internalResource(
        id: Int = 1,
        department: Department = Department(id = 3, name = "Operations"),
        quantity: Int = 4,
        unavailable: Int = 1,
    ) = Resource(
        id = id,
        code = "R-$id",
        name = "Laptop",
        type = ResourceType.INTERNAL,
        category = ResourceCategory.EQUIPMENT,
        unitOfMeasure = "unit",
        quantity = quantity,
        unavailableQuantity = unavailable,
        department = department,
    )

    @Test
    fun `utilisation reports total issued overdue and documented rate`() {
        val resource = internalResource()
        whenever(resources.findAll(any<org.springframework.data.jpa.domain.Specification<Resource>>(), eq(pageable)))
            .thenReturn(PageImpl(listOf(resource), pageable, 1))
        whenever(issues.summarizeResources(listOf(resource.id), LocalDate.parse("2026-10-26")))
            .thenReturn(
                listOf(object : ResourceIssueSummary {
                    override val resourceId = resource.id
                    override val totalIssuedQuantity = 9L
                    override val outstandingQuantity = 5L
                    override val overdueQuantity = 2L
                }),
            )

        val result = service.utilisation(caller(9, Role.ADMIN), null, null, null, pageable).content.single()

        assertEquals(10L, result.totalQuantity)
        assertEquals(9L, result.totalIssuedQuantity)
        assertEquals(5L, result.currentlyIssuedQuantity)
        assertEquals("50.00", result.utilisationRate.toPlainString())
        assertEquals(2L, result.overdueReturnQuantity)
    }

    @Test
    fun `department manager is limited to their own department`() {
        val manager = User(
            id = 9,
            username = "manager",
            email = "manager@example.test",
            role = Role.DEPARTMENT_MANAGER,
            department = Department(id = 3, name = "Operations"),
        )
        whenever(users.findById(manager.id)).thenReturn(Optional.of(manager))

        val exception = assertThrows(ResponseStatusException::class.java) {
            service.availabilityReport(caller(manager.id, manager.role), 4, null, null, pageable)
        }

        assertEquals(HttpStatus.FORBIDDEN, exception.statusCode)
        verify(resources, never()).findAll(any<org.springframework.data.jpa.domain.Specification<Resource>>(), any<org.springframework.data.domain.Pageable>())
    }

    @Test
    fun `department manager waitlist query is scoped to their department`() {
        val manager = User(
            id = 9,
            username = "manager",
            email = "manager@example.test",
            role = Role.DEPARTMENT_MANAGER,
            department = Department(id = 3, name = "Operations"),
        )
        whenever(users.findById(manager.id)).thenReturn(Optional.of(manager))
        whenever(lines.findApprovedWaitlist(ResourceType.INTERNAL, RequestStatus.APPROVED, 3, pageable))
            .thenReturn(PageImpl(emptyList(), pageable, 0))

        service.waitlist(caller(manager.id, manager.role), null, pageable)

        verify(lines).findApprovedWaitlist(ResourceType.INTERNAL, RequestStatus.APPROVED, 3, pageable)
    }

    @Test
    fun `team leads cannot retrieve inventory reports`() {
        assertThrows(ResponseStatusException::class.java) {
            service.utilisation(caller(9, Role.TEAM_LEAD), null, null, null, pageable)
        }
        assertThrows(ResponseStatusException::class.java) {
            service.availabilityReport(caller(9, Role.TEAM_LEAD), null, null, null, pageable)
        }
        assertThrows(ResponseStatusException::class.java) {
            service.waitlist(caller(9, Role.TEAM_LEAD), null, pageable)
        }

        verify(resources, never()).findAll(any<org.springframework.data.jpa.domain.Specification<Resource>>(), any<org.springframework.data.domain.Pageable>())
        verify(lines, never()).findApprovedWaitlist(any(), any(), any(), any())
    }

    @Test
    fun `waitlist estimates when known returns can meet the shortage`() {
        val line = object : WaitlistLineProjection {
            override val lineItemId = 5
            override val requestId = 11
            override val requestNumber = "REQ-2026-000011"
            override val resourceId = 1
            override val resourceName = "Laptop"
            override val requestStatus = RequestStatus.APPROVED
            override val remainingQuantity = 5L
            override val requiredDate = LocalDate.parse("2026-11-20")
        }
        val resource = internalResource(quantity = 2, unavailable = 0)
        whenever(lines.findApprovedWaitlist(ResourceType.INTERNAL, RequestStatus.APPROVED, null, pageable))
            .thenReturn(PageImpl(listOf(line), pageable, 1))
        whenever(resources.findAllById(listOf(1))).thenReturn(listOf(resource))
        whenever(availability.availableQuantity(resource)).thenReturn(2)
        whenever(issues.findExpectedReturns(listOf(1), LocalDate.parse("2026-10-26")))
            .thenReturn(
                listOf(object : ExpectedReturnProjection {
                    override val resourceId = 1
                    override val expectedDate = LocalDate.parse("2026-11-01")
                    override val quantity = 3L
                }),
            )

        val result = service.waitlist(caller(9, Role.ADMIN), null, pageable).content.single()

        assertEquals(5L, result.remainingQuantity)
        assertEquals(2, result.availableQuantity)
        assertEquals(LocalDate.parse("2026-11-01"), result.expectedAvailabilityDate)
    }

    @Test
    fun `reconciliation records variance without changing stock by default`() {
        val resource = internalResource()
        whenever(resources.findForUpdateById(resource.id)).thenReturn(resource)
        whenever(reconciliations.save(any())).thenAnswer { it.getArgument(0) }

        val report = service.reconcile(
            resource.id,
            9,
            Role.ADMIN,
            ReconciliationRequest(physicalCount = 6, reason = "Cycle count"),
        )

        assertEquals(4, report.systemQuantity)
        assertEquals(6, report.physicalCount)
        assertEquals(2, report.variance)
        assertEquals(false, report.adjustmentApplied)
        verify(stockOperations, never()).correctResource(any(), any(), any())
        verify(reconciliations).save(any())
    }

    @Test
    fun `reconciliation adjustment uses audited stock correction`() {
        val resource = internalResource()
        whenever(resources.findForUpdateById(resource.id)).thenReturn(resource)
        whenever(reconciliations.save(any())).thenAnswer { it.getArgument(0) }

        val report = service.reconcile(
            resource.id,
            9,
            Role.ADMIN,
            ReconciliationRequest(physicalCount = 6, reason = "Cycle count", adjustStock = true),
        )

        assertEquals(true, report.adjustmentApplied)
        verify(stockOperations).correctResource(resource.id, 9, StockCorrectionRequest(6, "Inventory reconciliation: Cycle count"))
    }

    @Test
    fun `only admins may reconcile stock`() {
        val exception = assertThrows(ResponseStatusException::class.java) {
            service.reconcile(
                1,
                9,
                Role.MANAGING_DIRECTOR,
                ReconciliationRequest(physicalCount = 6, reason = "Cycle count"),
            )
        }

        assertEquals(HttpStatus.FORBIDDEN, exception.statusCode)
        verify(resources, never()).findForUpdateById(any())
    }
}
