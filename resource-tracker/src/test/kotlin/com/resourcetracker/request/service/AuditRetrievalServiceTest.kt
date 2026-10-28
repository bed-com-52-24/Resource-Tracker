package com.resourcetracker.request.service

import com.resourcetracker.request.entity.RequestHistory
import com.resourcetracker.request.repository.RequestHistoryRepository
import com.resourcetracker.user.Role
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
import java.time.LocalDateTime
import java.time.ZoneOffset

class AuditRetrievalServiceTest {
    private val history = org.mockito.kotlin.mock<RequestHistoryRepository>()
    private val clock = Clock.fixed(Instant.parse("2026-10-26T00:00:00Z"), ZoneOffset.UTC)
    private val service = AuditRetrievalService(history, clock)
    private val pageable = PageRequest.of(0, 20)

    @Test
    fun `actor query defaults to 30 calendar days for admin and MD`() {
        whenever(
            history.findAllByActorIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanEqualOrderByCreatedAtAscIdAsc(
                eq(9), any(), any(), any(),
            ),
        ).thenReturn(PageImpl(emptyList()))

        service.byActor(9, Role.ADMIN, null, null, pageable)

        verify(history).findAllByActorIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanEqualOrderByCreatedAtAscIdAsc(
            9,
            LocalDateTime.parse("2026-09-27T00:00:00"),
            LocalDateTime.parse("2026-10-26T23:59:59.999999999"),
            pageable,
        )
        verify(history, never()).save(any())
    }

    @Test
    fun `actor query rejects ranges longer than one year`() {
        val exception = assertThrows(ResponseStatusException::class.java) {
            service.byActor(9, Role.MANAGING_DIRECTOR, LocalDate.parse("2025-10-25"), LocalDate.parse("2026-10-26"), pageable)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        assertEquals("Date range cannot exceed one year", exception.reason)
        verifyNoHistoryQuery()
    }

    @Test
    fun `action query accepts event types for a valid date range`() {
        whenever(
            history.findAllByEventTypeInAndCreatedAtGreaterThanEqualAndCreatedAtLessThanEqualOrderByCreatedAtAscIdAsc(
                any<Collection<String>>(), any(), any(), any(),
            ),
        ).thenReturn(PageImpl(emptyList()))

        service.byActions(listOf(" approved ", "ISSUED"), Role.MANAGING_DIRECTOR, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-26"), pageable)

        verify(history).findAllByEventTypeInAndCreatedAtGreaterThanEqualAndCreatedAtLessThanEqualOrderByCreatedAtAscIdAsc(
            listOf("APPROVED", "ISSUED"),
            LocalDateTime.parse("2026-10-01T00:00:00"),
            LocalDateTime.parse("2026-10-26T23:59:59.999999999"),
            pageable,
        )
    }

    @Test
    fun `action query rejects ranges longer than one year`() {
        val exception = assertThrows(ResponseStatusException::class.java) {
            service.byActions(listOf("APPROVED"), Role.ADMIN, LocalDate.parse("2025-10-25"), LocalDate.parse("2026-10-26"), pageable)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        assertEquals("Date range cannot exceed one year", exception.reason)
        verifyNoHistoryQuery()
    }

    @Test
    fun `item query supports requests and line items for admins`() {
        val entry = RequestHistory(id = 1, requestId = 3, eventType = "APPROVED", actorId = 7, lineItemId = 5)
        whenever(history.findAllByRequestIdOrderByCreatedAtAscIdAsc(3)).thenReturn(listOf(entry))
        whenever(history.findAllByLineItemIdOrderByCreatedAtAscIdAsc(5)).thenReturn(listOf(entry))

        assertEquals(listOf(1), service.byItem("request", 3, Role.ADMIN).map { it.id })
        assertEquals(listOf(1), service.byItem("LINE_ITEM", 5, Role.ADMIN).map { it.id })
        verify(history, never()).save(any())
    }

    @Test
    fun `filtered retrieval endpoints enforce their role scopes`() {
        val actorException = assertThrows(ResponseStatusException::class.java) {
            service.byActor(9, Role.TEAM_LEAD, null, null, pageable)
        }
        val actionException = assertThrows(ResponseStatusException::class.java) {
            service.byActions(listOf("APPROVED"), Role.DEPARTMENT_MANAGER, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-26"), pageable)
        }
        val itemException = assertThrows(ResponseStatusException::class.java) {
            service.byItem("REQUEST", 9, Role.MANAGING_DIRECTOR)
        }

        assertEquals(HttpStatus.FORBIDDEN, actorException.statusCode)
        assertEquals(HttpStatus.FORBIDDEN, actionException.statusCode)
        assertEquals(HttpStatus.FORBIDDEN, itemException.statusCode)
        verifyNoHistoryQuery()
    }

    private fun verifyNoHistoryQuery() {
        verify(history, never()).findAllByActorIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanEqualOrderByCreatedAtAscIdAsc(
            any(), any(), any(), any(),
        )
        verify(history, never()).findAllByEventTypeInAndCreatedAtGreaterThanEqualAndCreatedAtLessThanEqualOrderByCreatedAtAscIdAsc(
            any<Collection<String>>(), any(), any(), any(),
        )
        verify(history, never()).findAllByRequestIdOrderByCreatedAtAscIdAsc(any())
        verify(history, never()).findAllByLineItemIdOrderByCreatedAtAscIdAsc(any())
    }
}
