package com.resourcetracker.approval

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import org.springframework.security.core.context.SecurityContextHolder
import com.resourcetracker.user.UserRepository
import java.util.Optional
import java.util.stream.Stream

class ApprovalServiceTest {
    private val requests = mock(ApprovalRequestRepository::class.java)
    private val lines = mock(ApprovalLineItemRepository::class.java)
    private val audits = mock(ApprovalAuditRepository::class.java)
    @Suppress("UNCHECKED_CAST")
    private val internal = mock(ObjectProvider::class.java) as ObjectProvider<InternalApprovalHandler>
    @Suppress("UNCHECKED_CAST")
    private val external = mock(ObjectProvider::class.java) as ObjectProvider<ExternalApprovalHandler>
    private val notifier = mock(RequestNotifier::class.java)
    private val service = ApprovalService(requests, lines, audits, internal, external, notifier)

    private fun request(status: String = "PENDING") = ApprovalRequest(
        id = 9,
        assignedManagerId = 8,
        requesterId = 7,
        status = status,
    )

    private fun configureRequest(request: ApprovalRequest) {
        `when`(requests.findById(9)).thenReturn(Optional.of(request))
        `when`(requests.saveAndFlush(request)).thenReturn(request)
        `when`(lines.findByRequestId(9)).thenReturn(emptyList())
        `when`(internal.orderedStream()).thenReturn(Stream.empty())
        `when`(external.orderedStream()).thenReturn(Stream.empty())
    }

    @Test
    fun `approval decision accepts whole-request approve`() {
        val request = request()
        configureRequest(request)

        val result = service.decide(9, 8, DecisionRequest(ApprovalDecision.APPROVE))

        assertEquals("APPROVED", result.newStatus)
        assertEquals("APPROVED", request.status)
    }

    @Test
    fun `reject and query require comments`() {
        configureRequest(request())

        val rejectError = assertThrows<ResponseStatusException> {
            service.decide(9, 8, DecisionRequest(ApprovalDecision.REJECT))
        }
        assertEquals(HttpStatus.BAD_REQUEST, rejectError.statusCode)

        val queryError = assertThrows<ResponseStatusException> {
            service.decide(9, 8, DecisionRequest(ApprovalDecision.QUERY))
        }
        assertEquals(HttpStatus.BAD_REQUEST, queryError.statusCode)
    }

    @Test
    fun `decision rejects non-pending requests and unassigned managers`() {
        configureRequest(request(status = "QUERIED"))
        val stateError = assertThrows<ResponseStatusException> {
            service.decide(9, 8, DecisionRequest(ApprovalDecision.APPROVE))
        }
        assertEquals(HttpStatus.CONFLICT, stateError.statusCode)

        val ownerError = assertThrows<ResponseStatusException> {
            service.decide(9, 5, DecisionRequest(ApprovalDecision.APPROVE))
        }
        assertEquals(HttpStatus.FORBIDDEN, ownerError.statusCode)
    }

    @Test
    fun `missing authentication is unauthorized`() {
        SecurityContextHolder.clearContext()

        val error = assertThrows<ResponseStatusException> {
            CurrentUser(mock(UserRepository::class.java)).id()
        }

        assertEquals(HttpStatus.UNAUTHORIZED, error.statusCode)
    }
}
