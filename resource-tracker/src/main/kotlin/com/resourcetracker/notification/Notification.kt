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
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

@Entity
@Table(name = "notifications")
class Notification(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    var id: Long = 0,

    @Column(name = "recipient_id", nullable = false)
    var recipientId: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    var type: NotificationType,

    @Column(name = "request_id", length = 64)
    var requestId: String? = null,

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    var message: String,

    @Column(name = "read_flag", nullable = false)
    var read: Boolean = false,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)

interface NotificationRepository : JpaRepository<Notification, Long> {
    fun findByRecipientIdOrderByCreatedAtDesc(recipientId: Int, pageable: Pageable): Page<Notification>

    fun findByRecipientIdAndReadOrderByCreatedAtDesc(recipientId: Int, read: Boolean, pageable: Pageable): Page<Notification>

    fun countByRecipientIdAndRead(recipientId: Int, read: Boolean): Long

    @Query(
        """
        select n from Notification n
        where n.recipientId = :recipientId
          and (:unreadOnly = false or n.read = false)
          and (:search is null or lower(n.message) like lower(concat('%', :search, '%'))
            or lower(n.type) like lower(concat('%', :search, '%')))
        order by n.createdAt desc
        """
    )
    fun searchForUser(
        @Param("recipientId") recipientId: Int,
        @Param("unreadOnly") unreadOnly: Boolean,
        @Param("search") search: String,
        pageable: Pageable,
    ): Page<Notification>

    fun findByIdAndRecipientId(id: Long, recipientId: Int): Notification?
}
