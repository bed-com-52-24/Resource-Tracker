package com.resourcetracker.request.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

enum class ReservationStatus { ACTIVE, CONSUMED, RELEASED, EXPIRED }
enum class ReturnStatus { PENDING, VERIFIED, REJECTED }
enum class ReturnCondition { USABLE, DAMAGED, LOST, QUARANTINED }
enum class MovementType { ISSUE, RETURN_USABLE, RETURN_UNAVAILABLE, CORRECTION, OPENING }
enum class ProcurementStatus { AUTHORISED, ORDERED, RECEIVED, ACCEPTED, CANCELLED }

@Entity
@Table(name = "stock_reservations")
class StockReservation(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "reservation_id") var id: Int = 0,
    @Column(name = "resource_id", nullable = false) var resourceId: Int,
    @Column(name = "request_id", nullable = false) var requestId: Int,
    @Column(name = "line_item_id", nullable = false) var lineItemId: Int,
    @Column(name = "team_id", nullable = false) var teamId: Int,
    @Column(nullable = false) var quantity: Int,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) var status: ReservationStatus = ReservationStatus.ACTIVE,
    @Column(name = "expires_at") var expiresAt: LocalDateTime? = null,
    @Column(name = "created_by", nullable = false) var createdBy: Int,
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) var createdAt: LocalDateTime? = null,
    @Column(name = "closed_at") var closedAt: LocalDateTime? = null,
    @Column(name = "close_reason", length = 255) var closeReason: String? = null,
)

@Entity
@Table(name = "stock_issues")
class StockIssue(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "issue_id") var id: Int = 0,
    @Column(name = "request_id", nullable = false) var requestId: Int,
    @Column(name = "line_item_id", nullable = false) var lineItemId: Int,
    @Column(name = "resource_id", nullable = false) var resourceId: Int,
    @Column(name = "team_id", nullable = false) var teamId: Int,
    @Column(name = "reservation_id") var reservationId: Int? = null,
    @Column(nullable = false) var quantity: Int,
    @Column(name = "returned_quantity", nullable = false) var returnedQuantity: Int = 0,
    @Column(name = "issued_by", nullable = false) var issuedBy: Int,
    @Column(name = "issued_at", nullable = false, insertable = false, updatable = false) var issuedAt: LocalDateTime? = null,
)

@Entity
@Table(name = "stock_returns")
class StockReturn(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "return_id") var id: Int = 0,
    @Column(name = "issue_id", nullable = false) var issueId: Int,
    @Column(nullable = false) var quantity: Int,
    @Enumerated(EnumType.STRING) @Column(name = "condition_reported", length = 12) var condition: ReturnCondition? = null,
    @Column(name = "returned_by", nullable = false) var returnedBy: Int,
    @Column(name = "returned_at", nullable = false, insertable = false, updatable = false) var returnedAt: LocalDateTime? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) var status: ReturnStatus = ReturnStatus.PENDING,
    @Column(name = "verified_by") var verifiedBy: Int? = null,
    @Column(name = "verified_at") var verifiedAt: LocalDateTime? = null,
    @Column(length = 500) var note: String? = null,
)

@Entity
@Table(name = "stock_movements")
class StockMovement(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "movement_id") var id: Int = 0,
    @Column(name = "resource_id", nullable = false) var resourceId: Int,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) var type: MovementType,
    @Column(nullable = false) var quantity: Int,
    @Column(name = "request_id") var requestId: Int? = null,
    @Column(name = "issue_id") var issueId: Int? = null,
    @Column(name = "return_id") var returnId: Int? = null,
    @Column(name = "actor_id", nullable = false) var actorId: Int,
    @Column(length = 500) var reason: String? = null,
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) var createdAt: LocalDateTime? = null,
)

@Entity
@Table(name = "procurements")
class Procurement(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "procurement_id") var id: Int = 0,
    @Column(name = "request_id", nullable = false) var requestId: Int,
    @Column(name = "line_item_id", nullable = false) var lineItemId: Int,
    @Column(name = "vendor_id", nullable = false) var vendorId: Int,
    @Column(name = "estimated_cost", precision = 12, scale = 2) var estimatedCost: java.math.BigDecimal? = null,
    @Column(name = "expected_delivery") var expectedDelivery: LocalDate? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) var status: ProcurementStatus = ProcurementStatus.AUTHORISED,
    @Column(name = "received_quantity") var receivedQuantity: Int? = null,
    @Column(name = "received_by") var receivedBy: Int? = null,
    @Column(name = "received_at") var receivedAt: LocalDateTime? = null,
    @Column(name = "accepted_by") var acceptedBy: Int? = null,
    @Column(name = "accepted_at") var acceptedAt: LocalDateTime? = null,
)
