package com.resourcetracker.request.service

import com.resourcetracker.request.service.ExternalApprovalHandler
import com.resourcetracker.common.ResourceNotFoundException
import com.resourcetracker.department.Department
import com.resourcetracker.notification.NotificationService
import com.resourcetracker.notification.NotificationType
import com.resourcetracker.project.Project
import com.resourcetracker.project.ProjectRepository
import com.resourcetracker.request.dto.CreateResourceRequestDto
import com.resourcetracker.request.dto.RequestLineItemDto
import com.resourcetracker.request.entity.*
import com.resourcetracker.request.repository.*
import com.resourcetracker.resource.Resource
import com.resourcetracker.resource.ResourceCategory
import com.resourcetracker.resource.ResourceRepository
import com.resourcetracker.resource.ResourceType
import com.resourcetracker.team.Team
import com.resourcetracker.team.TeamRepository
import com.resourcetracker.user.Role
import com.resourcetracker.user.User
import com.resourcetracker.user.UserRepository
import com.resourcetracker.vendor.VendorRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.LocalDate
import java.util.Optional

class ResourceRequestWorkflowServiceTest {
    private val requests = org.mockito.kotlin.mock<ResourceRequestRepository>()
    private val lines = org.mockito.kotlin.mock<RequestLineItemRepository>()
    private val history = org.mockito.kotlin.mock<RequestHistoryRepository>()
    private val numbers = org.mockito.kotlin.mock<RequestNumberService>()
    private val teams = org.mockito.kotlin.mock<TeamRepository>()
    private val projects = org.mockito.kotlin.mock<ProjectRepository>()
    private val vendors = org.mockito.kotlin.mock<VendorRepository>()
    private val resources = org.mockito.kotlin.mock<ResourceRepository>()
    private val users = org.mockito.kotlin.mock<UserRepository>()
    private val notifications = org.mockito.kotlin.mock<NotificationService>()
    private val reservations = org.mockito.kotlin.mock<ReservationService>()
    private val availability = org.mockito.kotlin.mock<StockAvailabilityService>()
    private val reservationRows = org.mockito.kotlin.mock<StockReservationRepository>()
    private val issueRows = org.mockito.kotlin.mock<StockIssueRepository>()
    private val waitlistEntries = org.mockito.kotlin.mock<WaitlistEntryRepository>()
    private val procurements = org.mockito.kotlin.mock<ProcurementService>()
    private val waitlistService = org.mockito.kotlin.mock<WaitlistService>()
    private val externalApprovalHandler = org.mockito.kotlin.mock<ExternalApprovalHandler>()
    private val clock = Clock.systemUTC()

    private val department = Department(id = 1, name = "Operations")
    private val team = Team(id = 8, name = "Field Team", department = department)
    private val project = Project(
        id = 12, name = "Facilities", code = "FAC-1", department = department,
        startDate = LocalDate.now(clock).minusDays(1), active = true,
    )
    private val caller = User(
        id = 7, username = "lead", email = "lead@example.test", fullName = "Team Lead",
        passwordHash = "hash", role = Role.TEAM_LEAD, department = department,
        teams = mutableSetOf(team),
    )

    private fun service() = ResourceRequestService(
        requests, lines, history, numbers, teams, projects, vendors, resources, users,
        notifications, reservations, availability, reservationRows, issueRows, waitlistEntries,
        procurements, waitlistService, externalApprovalHandler, clock,
    )

    private fun setup(managerList: List<User>) {
        team.projects.add(project)
        whenever(users.findById(7)).thenReturn(Optional.of(caller))
        whenever(users.findActiveByRoleAndDepartmentId(Role.DEPARTMENT_MANAGER, department.id)).thenReturn(managerList)
        whenever(users.findActiveByRole(Role.ADMIN)).thenReturn(emptyList())
        whenever(teams.findById(team.id)).thenReturn(Optional.of(team))
        whenever(projects.findById(project.id)).thenReturn(Optional.of(project))
        whenever(numbers.generateRequestNumber()).thenReturn("REQ-2026-000001")
        whenever(requests.save(any())).thenAnswer { invocation ->
            invocation.getArgument<ResourceRequest>(0).apply { requestId = 101 }
        }
        whenever(lines.saveAll(any<Iterable<ResourceRequestLineItem>>())).thenAnswer { invocation ->
            invocation.getArgument<Iterable<ResourceRequestLineItem>>(0).toList().onEachIndexed { index, line -> line.lineItemId = index + 1 }
        }
        whenever(reservationRows.findAllByRequestIdAndStatus(101, ReservationStatus.ACTIVE)).thenReturn(emptyList())
        whenever(issueRows.findAllByRequestId(101)).thenReturn(emptyList())
    }

    private fun body() = CreateResourceRequestDto(
        teamId = team.id,
        projectId = project.id,
        justification = "Needed for field work",
        requiredDate = LocalDate.now(clock).plusDays(2),
        lineItems = listOf(RequestLineItemDto(description = "Safety cones", quantity = 4, requestedDurationDays = 30)),
    )

    @Test
    fun `external request line carries vendorId on the line item itself`() {
        setup(emptyList())
        val vendor = com.resourcetracker.vendor.Vendor(id = 5, name = "Northwind", status = "ACTIVE")
        whenever(vendors.findById(5)).thenReturn(Optional.of(vendor))

        val response = service().createRequest(
            body().copy(
                lineItems = listOf(
                    RequestLineItemDto(
                        description = "Safety cones",
                        vendorId = 5,
                        manufacturer = null,
                        unitOfMeasure = null,
                        quantity = 4,
                        fulfillmentPreference = FulfillmentPreference.EXTERNAL,
                        unitPrice = java.math.BigDecimal("12.50"),
                    ),
                ),
            ),
            caller.id,
        )

        assertEquals(1, response.lineItems.size)
        assertEquals(5, response.lineItems.first().vendorId)
        assertEquals(null, response.lineItems.first().manufacturer)
        assertEquals(null, response.lineItems.first().unitOfMeasure)
    }

    @Test
    fun `request without exactly one manager is saved flagged and admins notified`() {
        setup(emptyList())

        val response = service().createRequest(body(), caller.id)

        val request = argumentCaptor<ResourceRequest>()
        verify(requests).save(request.capture())
        assertEquals(RequestStatus.PENDING, request.firstValue.status)
        assertEquals(null, request.firstValue.assignedManagerId)
        assertTrue(request.firstValue.needsReassignment)
        val events = argumentCaptor<RequestHistory>()
        verify(history, org.mockito.kotlin.times(2)).save(events.capture())
        assertEquals(setOf("SUBMITTED", "ASSIGNED"), events.allValues.map { it.eventType }.toSet())
        verify(notifications).notify(eq(emptyList()), eq(NotificationType.REASSIGNMENT_REQUIRED), any())
        assertEquals("REQ-2026-000001", response.requestNumber)
        assertEquals(RequestStatus.PENDING, response.status)
    }

    @Test
    fun `request for another department is forbidden before it is persisted`() {
        val otherDepartment = Department(id = 2, name = "Finance")
        val foreignTeam = Team(id = 9, name = "Finance Team", department = otherDepartment)
        caller.teams.add(foreignTeam)
        whenever(users.findById(caller.id)).thenReturn(Optional.of(caller))
        whenever(teams.findById(foreignTeam.id)).thenReturn(Optional.of(foreignTeam))
        val foreignProject = Project(id = 13, name = "Budget", code = "BUD-1", department = otherDepartment, startDate = LocalDate.now(clock))
        whenever(projects.findById(foreignProject.id)).thenReturn(Optional.of(foreignProject))

        val exception = assertThrows<ResponseStatusException> {
            service().createRequest(body().copy(teamId = foreignTeam.id, projectId = foreignProject.id), caller.id)
        }

        assertEquals(HttpStatus.FORBIDDEN, exception.statusCode)
        verify(requests, never()).save(any())
    }

    @Test
    fun `a manager who is not assigned cannot decide the request`() {
        val request = ResourceRequest(requestId = 101, requestNumber = "REQ-2026-000001", itemDescription = "Cones", manufacturer = "N/A", assignedManagerId = 55, requesterId = caller.id)
        whenever(requests.findForUpdateById(101)).thenReturn(request)
        val otherManager = User(
            id = 56, username = "other", email = "other@example.test", fullName = "Other Manager",
            passwordHash = "hash", role = Role.DEPARTMENT_MANAGER, department = department,
        )

        val exception = assertThrows<ResponseStatusException> {
            service().decide(101, otherManager, com.resourcetracker.request.dto.RequestDecisionDto(com.resourcetracker.request.dto.Decision.APPROVE))
        }

        assertEquals(HttpStatus.FORBIDDEN, exception.statusCode)
        verify(lines, never()).findAllByRequestId(101)
    }

    @Test
    fun `a queried request cannot be approved before clarification`() {
        val request = ResourceRequest(requestId = 101, requestNumber = "REQ-2026-000001", itemDescription = "Cones", manufacturer = "N/A", assignedManagerId = 55, requesterId = caller.id)
        whenever(requests.findForUpdateById(101)).thenReturn(request)
        val manager = User(
            id = 55, username = "manager", email = "manager@example.test", fullName = "Manager",
            passwordHash = "hash", role = Role.DEPARTMENT_MANAGER, department = department,
        )
        request.status = RequestStatus.QUERIED

        val exception = assertThrows<ResponseStatusException> {
            service().decide(101, manager, com.resourcetracker.request.dto.RequestDecisionDto(com.resourcetracker.request.dto.Decision.APPROVE))
        }

        assertEquals(HttpStatus.CONFLICT, exception.statusCode)
        verify(requests, never()).saveAndFlush(request)
    }

    @Test
    fun `approving a request reserves its internal lines through the stock reservation service`() {
        val manager = User(
            id = 55, username = "manager", email = "manager@example.test", fullName = "Manager",
            passwordHash = "hash", role = Role.DEPARTMENT_MANAGER, department = department,
        )
        val request = ResourceRequest(
            requestId = 101,
            requestNumber = "REQ-2026-000001",
            itemDescription = "Projector",
            manufacturer = "N/A",
            departmentId = department.id,
            projectId = project.id,
            teamId = team.id,
            requiredDate = LocalDate.now(clock).plusDays(3),
            assignedManagerId = manager.id,
            requesterId = caller.id,
        )
        val line = ResourceRequestLineItem(
            lineItemId = 20,
            requestId = 101,
            description = "Projector",
            manufacturer = "N/A",
            quantity = 2,
            resourceId = 4,
            fulfillmentPreference = FulfillmentPreference.INTERNAL.name,
            requestedDurationDays = 14,
        )
        val resource = Resource(
            id = 4,
            code = "R-4",
            name = "Projector",
            type = ResourceType.INTERNAL,
            category = ResourceCategory.EQUIPMENT,
            unitOfMeasure = "unit",
            quantity = 2,
        )
        whenever(requests.findForUpdateById(101)).thenReturn(request)
        whenever(requests.saveAndFlush(any())).thenAnswer { it.getArgument<ResourceRequest>(0) }
        whenever(lines.findAllByRequestId(101)).thenReturn(listOf(line))
        whenever(resources.findForUpdateById(resource.id)).thenReturn(resource)
        whenever(resources.findById(resource.id)).thenReturn(Optional.of(resource))
        whenever(users.findById(caller.id)).thenReturn(Optional.of(caller))
        whenever(users.findById(manager.id)).thenReturn(Optional.of(manager))
        whenever(teams.findById(team.id)).thenReturn(Optional.of(team))
        whenever(projects.findById(project.id)).thenReturn(Optional.of(project))
        whenever(availability.availableQuantity(resource, lockReservations = true)).thenReturn(2)

        service().decide(
            101,
            manager,
            com.resourcetracker.request.dto.RequestDecisionDto(com.resourcetracker.request.dto.Decision.APPROVE),
        )

        verify(reservations).reserveApprovedInternalLines(101, listOf(line), manager.id)
    }

    @Test
    fun `authorized caller receives request history in chronological order`() {
        val request = ResourceRequest(requestId = 101, requestNumber = "REQ-2026-000001", itemDescription = "Cones", manufacturer = "N/A", teamId = team.id, requesterId = caller.id)
        val submitted = RequestHistory(id = 1, requestId = 101, eventType = "SUBMITTED", actorId = caller.id)
        val approved = RequestHistory(id = 2, requestId = 101, eventType = "APPROVED", fromStatus = "PENDING", toStatus = "APPROVED", actorId = 55)
        whenever(requests.findById(101)).thenReturn(Optional.of(request))
        whenever(history.findAllByRequestIdOrderByCreatedAtAscIdAsc(101)).thenReturn(listOf(submitted, approved))

        val result = service().history(101, caller)

        assertEquals(listOf("SUBMITTED", "APPROVED"), result.map { it.eventType })
        assertEquals(listOf(1, 2), result.map { it.id })
    }

    @Test
    fun `caller cannot retrieve history for a request they do not own`() {
        val request = ResourceRequest(requestId = 101, requestNumber = "REQ-2026-000001", itemDescription = "Cones", manufacturer = "N/A", teamId = 99, requesterId = 55)
        whenever(requests.findById(101)).thenReturn(Optional.of(request))

        assertThrows<ResourceNotFoundException> { service().history(101, caller) }

        verify(history, never()).findAllByRequestIdOrderByCreatedAtAscIdAsc(101)
    }

    @Test
    fun `team lead who is not the request owner cannot retrieve its history`() {
        val request = ResourceRequest(requestId = 101, requestNumber = "REQ-2026-000001", itemDescription = "Cones", manufacturer = "N/A", teamId = team.id, requesterId = 55)
        whenever(requests.findById(101)).thenReturn(Optional.of(request))

        assertThrows<ResourceNotFoundException> { service().history(101, caller) }

        verify(history, never()).findAllByRequestIdOrderByCreatedAtAscIdAsc(101)
    }

    @Test
    fun `assigned manager and elevated roles can retrieve request history`() {
        val request = ResourceRequest(requestId = 101, requestNumber = "REQ-2026-000001", itemDescription = "Cones", manufacturer = "N/A", requesterId = 55, assignedManagerId = 56)
        whenever(requests.findById(101)).thenReturn(Optional.of(request))
        whenever(history.findAllByRequestIdOrderByCreatedAtAscIdAsc(101)).thenReturn(emptyList())
        val manager = User(
            id = 56, username = "manager", email = "manager@example.test", fullName = "Manager",
            passwordHash = "hash", role = Role.DEPARTMENT_MANAGER, department = department,
        )
        val admin = User(
            id = 57, username = "admin", email = "admin@example.test", fullName = "Admin",
            passwordHash = "hash", role = Role.ADMIN, department = department,
        )
        val managingDirector = User(
            id = 58, username = "md", email = "md@example.test", fullName = "MD",
            passwordHash = "hash", role = Role.MANAGING_DIRECTOR, department = department,
        )

        assertTrue(service().history(101, manager).isEmpty())
        assertTrue(service().history(101, admin).isEmpty())
        assertTrue(service().history(101, managingDirector).isEmpty())

        verify(history, org.mockito.kotlin.times(3)).findAllByRequestIdOrderByCreatedAtAscIdAsc(101)
    }
}
