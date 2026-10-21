package com.resourcetracker.notification

import com.resourcetracker.security.AuthenticatedUser
import com.resourcetracker.user.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

class NotificationServiceTest {

    private val repository = mock(NotificationRepository::class.java)
    private val service = NotificationService(repository)

    @Test
    fun `notify creates one notification per user`() {
        val userIds = listOf(1, 2)
        val payload = mapOf("requestId" to 42, "message" to "Request submitted")

        service.notify(userIds, NotificationType.SUBMITTED, payload)

        val captor = argumentCaptor<Iterable<Notification>>()
        verify(repository).saveAll(captor.capture())
        val notifications = captor.firstValue.toList()
        assertEquals(2, notifications.size)
        assertEquals(NotificationType.SUBMITTED, notifications[0].type)
        assertEquals(42, notifications[0].requestId)
        assertEquals("Request 42 has been submitted.", notifications[0].message)
        assertFalse(notifications[0].read)
    }

    @Test
    fun `list only returns notifications for caller`() {
        val caller = AuthenticatedUser(1, "alice", Role.ADMIN, "jti", Instant.now())
        val item = Notification(
            recipientId = 1,
            type = NotificationType.APPROVED,
            requestId = 9,
            message = "Approved",
            createdAt = Instant.now()
        )
        `when`(repository.findByRecipientIdOrderByCreatedAtDesc(1, Pageable.unpaged())).thenReturn(PageImpl(listOf(item)))

        val result = service.listForUser(caller.id, unreadOnly = false, search = null, Pageable.unpaged())

        assertEquals(1, result.size)
        assertEquals(1, result[0].recipientId)
    }

    @Test
    fun `read-state update only affects caller-owned notifications`() {
        val caller = AuthenticatedUser(2, "bob", Role.TEAM_LEAD, "jti", Instant.now())
        val item = Notification(
            id = 77,
            recipientId = 2,
            type = NotificationType.QUERIED,
            requestId = 3,
            message = "Query",
            createdAt = Instant.now()
        )
        `when`(repository.findAllByIdInAndRecipientId(setOf(77), 2)).thenReturn(listOf(item))

        val result = service.updateReadState(caller.id, NotificationUpdateRequest(ids = setOf(77)))

        assertTrue(item.read)
        assertEquals(1, result.updatedCount)
        verify(repository).findAllByIdInAndRecipientId(setOf(77), caller.id)
    }

    @Test
    fun `read-state update applies to all notifications for caller only`() {
        val item = Notification(
            recipientId = 2,
            type = NotificationType.QUERIED,
            requestId = 3,
            message = "Query",
        )
        `when`(repository.findByRecipientId(2)).thenReturn(listOf(item))

        val result = service.updateReadState(2, NotificationUpdateRequest(all = true, read = false))

        assertFalse(item.read)
        assertEquals(1, result.updatedCount)
        verify(repository).findByRecipientId(2)
    }

    @Test
    fun `read-state update rejects missing or conflicting targets`() {
        listOf(
            NotificationUpdateRequest(),
            NotificationUpdateRequest(ids = setOf(77), all = true),
            NotificationUpdateRequest(ids = emptySet()),
        ).forEach { request ->
            val error = assertThrows<ResponseStatusException> {
                service.updateReadState(2, request)
            }
            assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
        }
    }
}
