package com.resourcetracker.inventory

import java.util.UUID

data class InventoryLowStockEvent(
    val resourceId: UUID,
    val location: String,
    val availableQuantity: Int,
    val reorderLevel: Int,
)

fun interface InventoryNotificationHook {
    fun onLowStock(event: InventoryLowStockEvent)
}
