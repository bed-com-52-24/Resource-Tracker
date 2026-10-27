package com.resourcetracker.request.service

import com.resourcetracker.department.Department
import com.resourcetracker.notification.NotificationService
import com.resourcetracker.notification.NotificationType
import com.resourcetracker.project.Project
import com.resourcetracker.project.ProjectRepository
import com.resourcetracker.request.dto.CreateResourceRequestDto
import com.resourcetracker.request.dto.RequestLineItemDto
import com.resourcetracker.request.entity.*
import com.resourcetracker.request.repository.*
import com.resourcetracker.resource.ResourceRepository
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
        notifications, reservations, availability, reservationRows, issueRows, waitlistEntries, procurements, waitlistService, clock,
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
        lineItems = listOf(RequestLineItemDto(description = "Safety cones", quantity = 4)),
    )

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
        assertTrue(response.needsReassignment)
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
        whenever(requests.findById(101)).thenReturn(Optional.of(request))
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
        whenever(requests.findById(101)).thenReturn(Optional.of(request))
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
}
