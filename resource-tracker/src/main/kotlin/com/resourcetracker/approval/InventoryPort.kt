package com.resourcetracker.approval

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component
import java.nio.ByteBuffer
import java.util.UUID

/** Stock for one resource, added up over all its locations. */
data class Availability(val resourceId: UUID, val total: Int, val reserved: Int, val issued: Int) {
    val available: Int get() = total - reserved - issued
}

/** Stock before and after a reservation. */
data class Reservation(val before: Availability, val after: Availability)

/** What the approval code needs from inventory. */
interface InventoryPort {
    /** Null if the resource has no inventory rows at all. */
    fun availability(resourceId: UUID): Availability?

    /** Reserves stock only if enough is left in total. Returns null if there is not enough. */
    fun tryReserve(resourceId: UUID, quantity: Int, actorId: Int, reason: String): Reservation?

    fun expectedReturnDates(resourceId: UUID): List<String>
}

/** Reads and updates resource_inventory and writes inventory_audit_logs (tables from V7). */
@Component
class JdbcInventoryPort(private val jdbc: JdbcTemplate) : InventoryPort {

    private data class StockRow(val id: Long, val total: Int, val reserved: Int, val issued: Int) {
        val available: Int get() = total - reserved - issued
    }

    private val stockMapper = RowMapper<StockRow> { rs, _ ->
        StockRow(
            rs.getLong("id"),
            rs.getInt("total_quantity"),
            rs.getInt("reserved_quantity"),
            rs.getInt("issued_quantity")
        )
    }

    private fun sum(resourceId: UUID, rows: List<StockRow>) = Availability(
        resourceId, rows.sumOf { it.total }, rows.sumOf { it.reserved }, rows.sumOf { it.issued }
    )

    override fun availability(resourceId: UUID): Availability? {
        val rows = jdbc.query(
            "SELECT id, total_quantity, reserved_quantity, issued_quantity " +
                    "FROM resource_inventory WHERE resource_id = ?",
            stockMapper, toBytes(resourceId)
        )
        return if (rows.isEmpty()) null else sum(resourceId, rows)
    }

    /**
     * FOR UPDATE locks every inventory row of this resource until the approval
     * transaction ends. A second approval for the last unit waits here, then sees
     * the updated numbers and fails, so stock can never be over-reserved.
     */
    override fun tryReserve(resourceId: UUID, quantity: Int, actorId: Int, reason: String): Reservation? {
        val rows = jdbc.query(
            "SELECT id, total_quantity, reserved_quantity, issued_quantity FROM resource_inventory " +
                    "WHERE resource_id = ? " +
                    "ORDER BY (total_quantity - reserved_quantity - issued_quantity) DESC FOR UPDATE",
            stockMapper, toBytes(resourceId)
        )
        if (rows.isEmpty()) return null

        val before = sum(resourceId, rows)
        if (before.available < quantity) return null

        var remaining = quantity
        for (row in rows) {
            if (remaining == 0) break
            val take = minOf(remaining, row.available)
            if (take <= 0) continue

            jdbc.update(
                "UPDATE resource_inventory SET reserved_quantity = reserved_quantity + ?, " +
                        "version = version + 1 WHERE id = ?",
                take, row.id
            )
            jdbc.update(
                "INSERT INTO inventory_audit_logs (inventory_id, actor_id, action, reason, " +
                        "previous_total_quantity, previous_reserved_quantity, previous_issued_quantity, " +
                        "new_total_quantity, new_reserved_quantity, new_issued_quantity) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                row.id, actorId, "RESOURCE_RESERVED", reason.take(500),
                row.total, row.reserved, row.issued,
                row.total, row.reserved + take, row.issued
            )
            remaining -= take
        }
        return Reservation(before, before.copy(reserved = before.reserved + quantity))
    }

    /** No table of current holders and return dates exists yet, so this stays empty. */
    override fun expectedReturnDates(resourceId: UUID): List<String> = emptyList()

    /** resources.id is BINARY(16): the UUID as 16 bytes. */
    private fun toBytes(id: UUID): ByteArray =
        ByteBuffer.allocate(16).putLong(id.mostSignificantBits).putLong(id.leastSignificantBits).array()
}