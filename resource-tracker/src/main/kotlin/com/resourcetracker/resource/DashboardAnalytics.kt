package com.resourcetracker.resource

import com.resourcetracker.request.entity.RequestStatus
import com.resourcetracker.request.repository.ResourceRequestRepository
import com.resourcetracker.request.repository.StockIssueRepository
import com.resourcetracker.request.repository.StockReservationRepository
import com.resourcetracker.security.AuthenticatedUser
import com.resourcetracker.user.Role
import com.resourcetracker.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.math.RoundingMode

data class DashboardAnalytics(
    val departmentId: Int?,
    val requestsByStatus: Map<RequestStatus, Long>,
    val inventory: DashboardInventoryAnalytics,
)

data class DashboardInventoryAnalytics(
    val internalResourceCount: Long,
    val onHandQuantity: Long,
    val reservedQuantity: Long,
    val unavailableQuantity: Long,
    val availableQuantity: Long,
    val issuedQuantity: Long,
    val utilisationRate: BigDecimal,
)

@Service
class DashboardAnalyticsService(
    private val resources: ResourceRepository,
    private val requests: ResourceRequestRepository,
    private val issues: StockIssueRepository,
    private val reservations: StockReservationRepository,
    private val users: UserRepository,
) {
    @Transactional(readOnly = true)
    fun analytics(caller: AuthenticatedUser, requestedDepartmentId: Int?): DashboardAnalytics {
        val departmentId = departmentScope(caller, requestedDepartmentId)
        val resourceTotals = resources.summarizeInternalStock(departmentId)
        val onHand = resourceTotals.getOnHandQuantity()
        val reserved = reservations.sumActiveByDepartmentId(departmentId)
        val unavailable = resourceTotals.getUnavailableQuantity()
        val issued = issues.sumOutstandingByDepartmentId(departmentId)
        val managedQuantity = onHand + unavailable + issued
        val utilisationRate = if (managedQuantity == 0L) {
            BigDecimal.ZERO.setScale(2)
        } else {
            BigDecimal.valueOf(issued)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(managedQuantity), 2, RoundingMode.HALF_UP)
        }
        val requestCounts = requests.summarizeStatusesByDepartment(departmentId)
            .associate { it.getStatus() to it.getRequestCount() }

        return DashboardAnalytics(
            departmentId = departmentId,
            requestsByStatus = RequestStatus.entries.associateWith { requestCounts[it] ?: 0L },
            inventory = DashboardInventoryAnalytics(
                internalResourceCount = resourceTotals.getResourceCount(),
                onHandQuantity = onHand,
                reservedQuantity = reserved,
                unavailableQuantity = unavailable,
                availableQuantity = (onHand - reserved - unavailable).coerceAtLeast(0),
                issuedQuantity = issued,
                utilisationRate = utilisationRate,
            ),
        )
    }

    private fun departmentScope(caller: AuthenticatedUser, requestedDepartmentId: Int?): Int? {
        if (requestedDepartmentId != null && requestedDepartmentId < 1) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "departmentId must be positive")
        }
        return when (caller.role) {
            Role.ADMIN, Role.MANAGING_DIRECTOR -> requestedDepartmentId
            Role.DEPARTMENT_MANAGER -> {
                val departmentId = users.findById(caller.id)
                    .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }
                    .department?.id
                    ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Department manager must belong to a department")
                if (requestedDepartmentId != null && requestedDepartmentId != departmentId) forbidden()
                departmentId
            }
            else -> forbidden()
        }
    }

    private fun forbidden(): Nothing =
        throw ResponseStatusException(HttpStatus.FORBIDDEN, "Action is not permitted")
}
