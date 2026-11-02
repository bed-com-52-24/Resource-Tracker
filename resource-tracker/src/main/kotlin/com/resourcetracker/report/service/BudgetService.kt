package com.resourcetracker.report.service

import com.resourcetracker.report.dto.BudgetResponse
import com.resourcetracker.report.dto.UpsertBudgetRequest
import com.resourcetracker.report.entity.BudgetAuditAction
import com.resourcetracker.report.entity.DepartmentBudget
import com.resourcetracker.report.entity.DepartmentBudgetAudit
import com.resourcetracker.report.repository.DepartmentBudgetAuditRepository
import com.resourcetracker.report.repository.DepartmentBudgetRepository
import com.resourcetracker.report.repository.ReportQueryRepository
import com.resourcetracker.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDateTime

@Service
class BudgetService(
    private val users: UserRepository,
    private val queries: ReportQueryRepository,
    private val budgets: DepartmentBudgetRepository,
    private val audits: DepartmentBudgetAuditRepository,
    private val clock: Clock
) {

    @Transactional
    fun upsert(
        actorId: Int,
        dto: UpsertBudgetRequest
    ): BudgetResponse {

        val actor = users.findById(actorId)
            .orElseThrow {
                ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "User not found"
                )
            }

        if (actor.role.name != "ADMIN") {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Only administrators can manage budgets"
            )
        }

        val amount = dto.allocatedAmount.setScale(
            2,
            RoundingMode.HALF_UP
        )

        val currency = dto.currency.trim().uppercase()

        if (amount.signum() < 0) {
            badRequest("Allocated amount cannot be negative")
        }

        if (!currency.matches(Regex("[A-Z]{3}"))) {
            badRequest("Currency must be a 3-letter code")
        }

        if (dto.periodEnd.isBefore(dto.periodStart)) {
            badRequest("Invalid budget period")
        }

        if (!queries.departmentExists(dto.departmentId)) {
            throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Department not found"
            )
        }

        if (dto.overrideBudget && dto.reason.isNullOrBlank()) {
            badRequest("A reason is required for an override")
        }

        val now = LocalDateTime.now(clock)
        val changedBy = actor.id.toString()

        val existing =
            budgets.findByDepartmentIdAndPeriodStartAndPeriodEnd(
                dto.departmentId,
                dto.periodStart,
                dto.periodEnd
            )

        val saved: DepartmentBudget

        if (existing == null) {

            saved = budgets.save(
                DepartmentBudget(
                    departmentId = dto.departmentId,
                    periodStart = dto.periodStart,
                    periodEnd = dto.periodEnd,
                    allocatedAmount = amount,
                    currency = currency,
                    createdBy = changedBy,
                    updatedBy = changedBy
                )
            )

            audits.save(
                DepartmentBudgetAudit(
                    budgetId = saved.id!!,
                    action = BudgetAuditAction.CREATED,
                    previousAmount = null,
                    newAmount = amount,
                    reason = dto.reason,
                    changedBy = changedBy,
                    changedAt = now
                )
            )

        } else {

            if (existing.currency != currency) {
                throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Currency cannot change for an existing budget"
                )
            }

            val previous = existing.allocatedAmount

            existing.allocatedAmount = amount
            existing.updatedBy = changedBy

            saved = budgets.save(existing)

            audits.save(
                DepartmentBudgetAudit(
                    budgetId = saved.id!!,
                    action = if (dto.overrideBudget) {
                        BudgetAuditAction.OVERRIDE
                    } else {
                        BudgetAuditAction.UPDATED
                    },
                    previousAmount = previous,
                    newAmount = amount,
                    reason = dto.reason,
                    changedBy = changedBy,
                    changedAt = now
                )
            )
        }

        return BudgetResponse(
            id = saved.id!!,
            departmentId = saved.departmentId,
            periodStart = saved.periodStart,
            periodEnd = saved.periodEnd,
            allocatedAmount = saved.allocatedAmount,
            currency = saved.currency
        )
    }

    private fun badRequest(message: String): Nothing {
        throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            message
        )
    }
}