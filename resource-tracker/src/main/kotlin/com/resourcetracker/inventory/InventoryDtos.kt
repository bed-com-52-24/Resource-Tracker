package com.resourcetracker.inventory

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
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

data class InventoryQuantityRequest(
    @field:Min(1) val quantity: Int,
)
