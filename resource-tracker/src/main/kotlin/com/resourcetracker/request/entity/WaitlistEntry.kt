package com.resourcetracker.request.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

enum class WaitlistStatus { WAITING, FULFILLABLE, FULFILLED, CANCELLED }

/** Who performed an audited action. SYSTEM = no human user (automatic fulfilment). */
enum class ActorType { USER, SYSTEM }

/**
 * One internal request line item waiting for stock.
 * Queue position is derived from createdAt (then id), never stored.
 * reservedQuantity is cumulative: units later issued stay counted as reserved.
 */
@Entity
@Table(name = "waitlist_entries")
class WaitlistEntry(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "waitlist_entry_id") var id: Int = 0,
    @Column(name = "request_id", nullable = false) var requestId: Int,
    @Column(name = "line_item_id", nullable = false) var lineItemId: Int,
    @Column(name = "resource_id", nullable = false) var resourceId: Int,
    @Column(nullable = false) var quantity: Int,
    @Column(name = "reserved_quantity", nullable = false) var reservedQuantity: Int = 0,
    @Column(name = "issued_quantity", nullable = false) var issuedQuantity: Int = 0,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) var status: WaitlistStatus = WaitlistStatus.WAITING,
    @Column(name = "expected_fulfillment_date") var expectedFulfillmentDate: LocalDate? = null,
    @Column(name = "overdue_notified_date") var overdueNotifiedDate: LocalDate? = null,
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) var createdAt: LocalDateTime? = null,
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false) var updatedAt: LocalDateTime? = null,
)
