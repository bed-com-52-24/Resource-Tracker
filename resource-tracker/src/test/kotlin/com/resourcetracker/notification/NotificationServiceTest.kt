package com.resourcetracker.notification

import com.resourcetracker.security.AuthenticatedUser
import com.resourcetracker.user.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
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
        assertEquals(NotificationStatus.UNREAD, notifications[0].status)
        assertEquals(null, notifications[0].readAt)
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

        val result = service.listForUser(caller.id, status = null, search = null, Pageable.unpaged())

        assertEquals(1, result.size)
        assertEquals(1, result[0].recipientId)
        assertEquals(NotificationStatus.UNREAD, result[0].status)
        assertEquals(null, result[0].readAt)
    }

    @Test
    fun `list filters by requested notification status`() {
        val item = Notification(
            recipientId = 1,
            type = NotificationType.APPROVED,
            message = "Approved",
            status = NotificationStatus.READ,
            readAt = Instant.now(),
        )
        `when`(
            repository.findByRecipientIdAndStatusOrderByCreatedAtDesc(
                1,
                NotificationStatus.READ,
                Pageable.unpaged(),
            ),
        ).thenReturn(PageImpl(listOf(item)))

        val result = service.listForUser(
            1,
            status = NotificationStatus.READ,
            search = null,
            pageable = Pageable.unpaged(),
        )

        assertEquals(1, result.size)
        assertEquals(NotificationStatus.READ, result.single().status)
        assertTrue(result.single().readAt != null)
    }

    @Test
    fun `status update only affects caller-owned notifications`() {
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

        val result = service.updateReadState(
            caller.id,
            NotificationUpdateRequest(ids = setOf(77), status = NotificationStatus.READ),
        )

        assertEquals(NotificationStatus.READ, item.status)
        assertTrue(item.readAt != null)
        assertEquals(1, result.updatedCount)
        verify(repository).findAllByIdInAndRecipientId(setOf(77), caller.id)
    }

    @Test
    fun `marking a notification unread clears its read timestamp`() {
        val item = Notification(
            recipientId = 2,
            type = NotificationType.QUERIED,
            requestId = 3,
            message = "Query",
            status = NotificationStatus.READ,
            readAt = Instant.now(),
        )
        `when`(repository.findAllByIdInAndRecipientId(setOf(77), 2)).thenReturn(listOf(item))

        val result = service.updateReadState(
            2,
            NotificationUpdateRequest(ids = setOf(77), status = NotificationStatus.UNREAD),
        )

        assertEquals(NotificationStatus.UNREAD, item.status)
        assertEquals(null, item.readAt)
        assertEquals(1, result.updatedCount)
    }

    @Test
    fun `marking all notifications uses a single status update scoped to the caller`() {
        `when`(
            repository.updateStatusForRecipient(
                eq(2),
                eq(NotificationStatus.READ),
                any(),
            ),
        ).thenReturn(3)

        val result = service.updateReadState(
            2,
            NotificationUpdateRequest(all = true, status = NotificationStatus.READ),
        )

        assertEquals(3, result.updatedCount)
        verify(repository).updateStatusForRecipient(
            eq(2),
            eq(NotificationStatus.READ),
            any(),
        )
        verify(repository, never()).findByRecipientId(2)
    }

    @Test
    fun `unread count counts only unread notifications`() {
        `when`(repository.countByRecipientIdAndStatus(2, NotificationStatus.UNREAD)).thenReturn(4)

        assertEquals(4, service.unreadCount(2))
        verify(repository).countByRecipientIdAndStatus(2, NotificationStatus.UNREAD)
    }

    @Test
    fun `read-state update rejects missing or conflicting targets`() {
        listOf(
            NotificationUpdateRequest(status = NotificationStatus.READ),
            NotificationUpdateRequest(ids = setOf(77), all = true, status = NotificationStatus.READ),
            NotificationUpdateRequest(ids = emptySet(), status = NotificationStatus.READ),
        ).forEach { request ->
            val error = assertThrows<ResponseStatusException> {
                service.updateReadState(2, request)
            }
            assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
        }
    }
}
