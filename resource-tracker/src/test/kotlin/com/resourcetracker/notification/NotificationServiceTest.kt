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
import java.time.Instant

class NotificationServiceTest {

    private val repository = mock(NotificationRepository::class.java)
    private val service = NotificationService(repository)

    @Test
    fun `notify creates one notification per user`() {
        val userIds = listOf(1, 2)
        val payload = mapOf("requestId" to "req-42", "message" to "Request submitted")

        service.notify(userIds, NotificationType.SUBMITTED, payload)

        val captor = argumentCaptor<Iterable<Notification>>()
        verify(repository).saveAll(captor.capture())
        val notifications = captor.firstValue.toList()
        assertEquals(2, notifications.size)
        assertEquals(NotificationType.SUBMITTED, notifications[0].type)
        assertEquals("req-42", notifications[0].requestId)
        assertEquals("Request req-42 has been submitted.", notifications[0].message)
        assertFalse(notifications[0].read)
    }

    @Test
    fun `list only returns notifications for caller`() {
        val caller = AuthenticatedUser(1, "alice", Role.ADMIN, "jti", Instant.now())
        val item = Notification(
            recipientId = 1,
            type = NotificationType.APPROVED,
            requestId = "req-9",
            message = "Approved",
            createdAt = Instant.now()
        )
        `when`(repository.findByRecipientIdOrderByCreatedAtDesc(1, Pageable.unpaged())).thenReturn(PageImpl(listOf(item)))

        val result = service.listForUser(caller.id, unreadOnly = false, search = null, Pageable.unpaged())

        assertEquals(1, result.size)
        assertEquals(1, result[0].recipientId)
    }

    @Test
    fun `mark read only affects caller owned notifications`() {
        val caller = AuthenticatedUser(2, "bob", Role.TEAM_LEAD, "jti", Instant.now())
        val item = Notification(
            id = 77,
            recipientId = 2,
            type = NotificationType.QUERIED,
            requestId = "req-3",
            message = "Query",
            createdAt = Instant.now()
        )
        `when`(repository.findByIdAndRecipientId(77, 2)).thenReturn(item)
        `when`(repository.save(item)).thenReturn(item)

        service.markRead(caller.id, 77)

        assertTrue(item.read)
    }

    @Test
    fun `mark read rejects other user notifications`() {
        val caller = AuthenticatedUser(2, "bob", Role.TEAM_LEAD, "jti", Instant.now())
        `when`(repository.findByIdAndRecipientId(77, 2)).thenReturn(null)

        assertThrows<IllegalArgumentException> {
            service.markRead(caller.id, 77)
        }
    }
}
