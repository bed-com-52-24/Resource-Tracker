package com.resourcetracker.inventory

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

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

data class AvailableQuantityResponse(
    val resourceId: UUID,
    val availableQuantity: Int,
)

enum class InventoryTransactionType{
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
    @Schema(description = "Quantity involved in the command", example = "5")
    val quantity: Int,

    @field:Size(max = 120)
    @Schema(description = "Required when the resource has multiple inventory locations")
    val location: String? = null,

    @field:Size(max = 500)
    @Schema(description = "Optional reason recorded in the audit log")
    val reason: String? = null,
)

data class InventoryTransactionResponse(
    val resourceId: UUID,
    val type: InventoryTransactionType,
    val quantity: Int,
    val inventory: InventoryResponse,
)
