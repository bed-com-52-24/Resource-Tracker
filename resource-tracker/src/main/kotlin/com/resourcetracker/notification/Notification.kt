package com.resourcetracker.notification

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

@Entity
@Table(name = "notifications")
class Notification(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    var id: Int = 0,

    @Column(name = "recipient_id", nullable = false)
    var recipientId: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    var type: NotificationType,

    @Column(name = "request_id")
    var requestId: Int? = null,

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    var message: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    var status: NotificationStatus = NotificationStatus.UNREAD,

    @Column(name = "read_at")
    var readAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) {
    fun setStatus(status: NotificationStatus, at: Instant = Instant.now()) {
        this.status = status
        readAt = if (status == NotificationStatus.READ) at else null
    }
}

interface NotificationRepository : JpaRepository<Notification, Int> {
    fun findByRecipientIdOrderByCreatedAtDesc(recipientId: Int, pageable: Pageable): Page<Notification>

    fun findByRecipientIdAndStatusOrderByCreatedAtDesc(
        recipientId: Int,
        status: NotificationStatus,
        pageable: Pageable,
    ): Page<Notification>

    fun countByRecipientIdAndStatus(recipientId: Int, status: NotificationStatus): Long

    @Query(
        """
        select n from Notification n
        where n.recipientId = :recipientId
          and (:status is null or n.status = :status)
          and (:search is null or lower(n.message) like lower(concat('%', :search, '%'))
            or lower(n.type) like lower(concat('%', :search, '%')))
        order by n.createdAt desc
        """
    )
    fun searchForUser(
        @Param("recipientId") recipientId: Int,
        @Param("status") status: NotificationStatus?,
        @Param("search") search: String,
        pageable: Pageable,
    ): Page<Notification>

    fun findByIdAndRecipientId(id: Int, recipientId: Int): Notification?

    fun findAllByIdInAndRecipientId(ids: Collection<Int>, recipientId: Int): List<Notification>

    fun findByRecipientId(recipientId: Int): List<Notification>

    @Modifying
    @Query(
        """
        update Notification n
        set n.status = :status, n.readAt = :readAt
        where n.recipientId = :recipientId and n.status <> :status
        """,
    )
    fun updateStatusForRecipient(
        @Param("recipientId") recipientId: Int,
        @Param("status") status: NotificationStatus,
        @Param("readAt") readAt: Instant?,
    ): Int
}
