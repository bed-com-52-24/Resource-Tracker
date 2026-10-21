package com.resourcetracker.inventory


data class InventoryLowStockEvent(
    val resourceId: Int,
    val location: String,
    val availableQuantity: Int,
    val reorderLevel: Int,
)

fun interface InventoryNotificationHook {
    fun onLowStock(event: InventoryLowStockEvent)
}
