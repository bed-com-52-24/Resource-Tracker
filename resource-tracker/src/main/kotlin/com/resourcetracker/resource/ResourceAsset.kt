package com.resourcetracker.resource

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.time.LocalDateTime

enum class AssetStatus { AVAILABLE, RESERVED, ISSUED, RETURN_PENDING, DAMAGED, MISSING, DISPOSED }
enum class AssetMovementType { REGISTERED, RESERVED, ISSUED, RETURN_SUBMITTED, RETURNED, CORRECTED, TRANSFERRED, DISPOSED }

@Entity
@Table(
    name = "resource_assets",
    uniqueConstraints = [UniqueConstraint(name = "uq_resource_assets_code", columnNames = ["asset_code"]), UniqueConstraint(name = "uq_resource_assets_serial", columnNames = ["serial_number"])],
    indexes = [Index(name = "idx_resource_assets_resource_status", columnList = "resource_id, status")],
)
class ResourceAsset(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "asset_id") var id: Int = 0,
    @Column(name = "resource_id", nullable = false) var resourceId: Int,
    @Column(name = "asset_code", nullable = false, unique = true, length = 80) var assetCode: String,
    @Column(name = "serial_number", unique = true, length = 120) var serialNumber: String? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) var status: AssetStatus = AssetStatus.AVAILABLE,
    @Column(length = 120) var location: String? = null,
    @Column(name = "assigned_user_id") var assignedUserId: Int? = null,
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) var createdAt: LocalDateTime? = null,
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
) {
    @PreUpdate
    fun touch() {
        updatedAt = Instant.now()
    }
}

@Entity
@Table(name = "asset_movements", indexes = [Index(name = "idx_asset_movements_asset_time", columnList = "asset_id, created_at")])
class AssetMovement(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "movement_id") var id: Int = 0,
    @Column(name = "asset_id", nullable = false) var assetId: Int,
    @Enumerated(EnumType.STRING) @Column(name = "movement_type", nullable = false, length = 20) var type: AssetMovementType,
    @Enumerated(EnumType.STRING) @Column(name = "previous_status", length = 20) var previousStatus: AssetStatus? = null,
    @Enumerated(EnumType.STRING) @Column(name = "new_status", nullable = false, length = 20) var newStatus: AssetStatus,
    @Column(name = "previous_location", length = 120) var previousLocation: String? = null,
    @Column(name = "new_location", length = 120) var newLocation: String? = null,
    @Column(name = "previous_assigned_user_id") var previousAssignedUserId: Int? = null,
    @Column(name = "new_assigned_user_id") var newAssignedUserId: Int? = null,
    @Column(nullable = false, length = 500) var reason: String,
    @Column(name = "actor_id", nullable = false) var actorId: Int,
    @Column(name = "related_request_id") var relatedRequestId: Int? = null,
    @Column(name = "related_issue_id") var relatedIssueId: Int? = null,
    @Column(name = "related_return_id") var relatedReturnId: Int? = null,
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) var createdAt: LocalDateTime? = null,
)

@Entity
@Table(name = "stock_issue_assets")
@IdClass(StockIssueAssetId::class)
class StockIssueAsset(
    @Id @Column(name = "issue_id") var issueId: Int,
    @Id @Column(name = "asset_id") var assetId: Int,
)

data class StockIssueAssetId(val issueId: Int = 0, val assetId: Int = 0) : java.io.Serializable

@Entity
@Table(name = "stock_return_assets")
@IdClass(StockReturnAssetId::class)
class StockReturnAsset(
    @Id @Column(name = "return_id") var returnId: Int,
    @Id @Column(name = "asset_id") var assetId: Int,
)

data class StockReturnAssetId(val returnId: Int = 0, val assetId: Int = 0) : java.io.Serializable

data class AssetRegistrationRequest(
    @field:jakarta.validation.constraints.NotBlank @field:jakarta.validation.constraints.Size(max = 80) val assetCode: String,
    @field:jakarta.validation.constraints.Size(max = 120) val serialNumber: String? = null,
    @field:jakarta.validation.constraints.Size(max = 120) val location: String? = null,
)

data class AssetCorrectionRequest(
    val status: AssetStatus,
    @field:jakarta.validation.constraints.NotBlank @field:jakarta.validation.constraints.Size(max = 500) val reason: String,
)

data class ResourceAssetResponse(
    val id: Int, val resourceId: Int, val assetCode: String, val serialNumber: String?,
    val status: AssetStatus, val location: String?, val assignedUserId: Int?, val createdAt: LocalDateTime?,
)

data class AssetMovementResponse(
    val id: Int, val type: AssetMovementType, val previousStatus: AssetStatus?, val newStatus: AssetStatus,
    val previousLocation: String?, val newLocation: String?, val reason: String, val actorId: Int, val createdAt: LocalDateTime?,
    val relatedIssueId: Int? = null, val relatedReturnId: Int? = null,
    val previousAssignedUserId: Int? = null, val newAssignedUserId: Int? = null,
)
