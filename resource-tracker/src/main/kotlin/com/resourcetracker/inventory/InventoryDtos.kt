package com.resourcetracker.inventory

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID
import com.resourcetracker.resource.ResourceCategory

enum class AvailabilityStatus {
    AVAILABLE, PARTIALLY_AVAILABLE, FULLY_RESERVED, OUT_OF_STOCK
}

@Schema(description = "Availability for an internal resource. AVAILABLE means stock is wholly free; PARTIALLY_AVAILABLE means some but not all stock is free; FULLY_RESERVED means no stock is free and at least one unit is reserved; OUT_OF_STOCK means no stock is free and none is reserved (including stock currently issued).")
data class ResourceAvailabilityResponse(
    val resourceId: UUID,
    val name: String,
    val category: ResourceCategory,
    val department: String?,
    val available: Int,
    val reserved: Int,
    val issued: Int,
    val status: AvailabilityStatus,
    @get:Schema(description = "Null until scheduled due-return data is introduced by RT-18")
    val nextExpectedAvailability: java.time.Instant? = null,
)

data class CreateInventoryRequest(
    @field:NotBlank @field:Size(max = 120)
    val location: String,
    @field:Min(0) val totalQuantity: Int,
    @field:Min(0) val reorderLevel: Int = 0,
    @field:NotBlank @field:Size(max = 500)
    val reason: String,
)

data class AdjustInventoryRequest(
    @field:NotBlank @field:Size(max = 120)
    @Schema(description = "Location of the inventory row to adjust", example = "WAREHOUSE-A")
    val location: String,
    @field:Min(0) val totalQuantity: Int,
    @field:Min(0) val reorderLevel: Int,
    @field:NotBlank @field:Size(max = 500)
    val reason: String,
)

data class InventoryResponse(
    val id: Long,
    val resourceId: UUID,
    val location: String,
    val totalQuantity: Int,
    val reservedQuantity: Int,
    val issuedQuantity: Int,
    @get:Schema(description = "Total quantity minus reserved and issued quantity")
    val availableQuantity: Int,
    val reorderLevel: Int,
    val lastUpdated: Instant,
)

data class InventoryListResponse(
    val resourceId: UUID,
    val totalAvailable: Int,
    val items: List<InventoryResponse>,
)

enum class InventoryTransactionType{
    ADJUST,
    RESERVE,
    RELEASE,
    ISSUE,
    RETURN,
}

data class InventoryTransactionRequest(
    @field:NotNull
    @Schema(description = "Inventory command to run", example = "RESERVE")
    val type: InventoryTransactionType,

    @field:Min(1)
    @Schema(description = "Quantity involved in the command. Required except for ADJUST.", example = "5")
    val quantity: Int? = null,

    @field:Size(max = 120)
    @Schema(description = "Required when the resource has multiple inventory locations")
    val location: String? = null,

    @field:Size(max = 500)
    @Schema(description = "Optional reason recorded in the audit log")
    val reason: String? = null,

    @field:Min(0)
    @Schema(description = "New total quantity. Required for ADJUST.")
    val totalQuantity: Int? = null,

    @field:Min(0)
    @Schema(description = "New reorder level. Required for ADJUST.")
    val reorderLevel: Int? = null,
)

data class InventoryTransactionResponse(
    val resourceId: UUID,
    val type: InventoryTransactionType,
    val quantity: Int?,
    val inventory: InventoryResponse,
)
