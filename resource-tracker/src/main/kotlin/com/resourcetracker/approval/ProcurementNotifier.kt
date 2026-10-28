package com.resourcetracker.approval

import com.resourcetracker.notification.NotificationService
import com.resourcetracker.notification.NotificationType
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component
import java.math.BigDecimal

/** Tells procurement that an external item was approved. */
interface ProcurementNotifier {
    fun itemApproved(requestId: Int, lineId: Int, amount: BigDecimal)
}

/**
 * Sends a PROCUREMENT notification through the notification module.
 * Goes to the user in app.procurement.user-id if set, otherwise to every active admin.
 */
@Component
class NotificationProcurementNotifier(
    private val notifications: NotificationService,
    private val jdbc: JdbcTemplate,
    @Value("\${app.procurement.user-id:0}") private val configuredUserId: Int
) : ProcurementNotifier {

    override fun itemApproved(requestId: Int, lineId: Int, amount: BigDecimal) {
        val recipients = if (configuredUserId > 0) listOf(configuredUserId) else adminIds()
        notifications.notify(
            recipients,
            NotificationType.PROCUREMENT,
            mapOf("requestId" to requestId, "lineId" to lineId, "amount" to amount)
        )
    }

    private fun adminIds(): List<Int> =
        jdbc.query(
            "SELECT user_id FROM users WHERE role = 'ADMIN' AND active = TRUE",
            RowMapper<Int> { rs, _ -> rs.getInt(1) }
        )
}
