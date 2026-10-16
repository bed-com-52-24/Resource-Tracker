package com.resourcetracker.approval

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component

/** Stock levels for one inventory item. */
data class Availability(val resourceId: Int, val total: Int, val reserved: Int, val issued: Int) {
    val available: Int get() = total - reserved - issued
}

/** What the approval code needs from inventory. */
interface InventoryPort {
    fun availability(resourceId: Int): Availability?

    /** Reserves stock only if enough is left. Returns false if there is not enough. */
    fun tryReserve(resourceId: Int, quantity: Int): Boolean

    fun expectedReturnDates(resourceId: Int): List<String>
}

/**
 * ASSUMED table: resource_inventory(resource_id, total_quantity, reserved_quantity, issued_quantity).
 * Adjust the names below once branch 32's migration has been checked.
 */
@Component
class JdbcInventoryPort(private val jdbc: JdbcTemplate) : InventoryPort {

    private val mapper = RowMapper<Availability> { rs, _ ->
        Availability(
            rs.getInt("resource_id"),
            rs.getInt("total_quantity"),
            rs.getInt("reserved_quantity"),
            rs.getInt("issued_quantity")
        )
    }

    override fun availability(resourceId: Int): Availability? =
        jdbc.query(
            "SELECT resource_id, total_quantity, reserved_quantity, issued_quantity " +
                    "FROM resource_inventory WHERE resource_id = ?",
            mapper, resourceId
        ).firstOrNull()

    /**
     * One UPDATE that only succeeds while enough stock remains. MySQL locks the row,
     * so two approvals for the last unit cannot both succeed (acceptance criterion).
     */
    override fun tryReserve(resourceId: Int, quantity: Int): Boolean =
        jdbc.update(
            "UPDATE resource_inventory SET reserved_quantity = reserved_quantity + ? " +
                    "WHERE resource_id = ? AND (total_quantity - reserved_quantity - issued_quantity) >= ?",
            quantity, resourceId, quantity
        ) == 1

    /** TODO: fill from the issued-items table on branch 32 (current holders' return dates). */
    override fun expectedReturnDates(resourceId: Int): List<String> = emptyList()
}