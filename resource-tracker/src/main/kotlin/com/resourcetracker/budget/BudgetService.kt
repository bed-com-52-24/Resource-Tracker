package com.resourcetracker.budget

import com.resourcetracker.audit.AuditAction
import com.resourcetracker.audit.AuditRecord
import com.resourcetracker.audit.AuditService
import com.resourcetracker.department.Department
import com.resourcetracker.request.entity.ResourceRequest
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

data class CreateBudgetRequest(
    val departmentId: Int,
    val periodType: BudgetPeriodType,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val amount: BigDecimal,
    val active: Boolean = true,
)

data class UpdateBudgetRequest(
    val amount: BigDecimal? = null,
    val active: Boolean? = null,
)

class BudgetService(
    private val budgetRepository: BudgetRepository,
    private val commitmentRepository: BudgetCommitmentRepository,
    private val auditService: AuditService? = null,
) {
    @Transactional
    fun create(request: CreateBudgetRequest): Budget {
        require(request.periodStart <= request.periodEnd) { "periodStart must not be after periodEnd" }
        require(request.amount >= BigDecimal.ZERO) { "amount must not be negative" }
        if (budgetRepository.existsActiveOverlap(request.departmentId, request.periodType, request.periodStart, request.periodEnd)) {
            throw IllegalStateException("An active budget already exists for this department and period")
        }

        val department = Department(id = request.departmentId, name = "Budget-${request.departmentId}")
        val budget = Budget(
            department = department,
            periodType = request.periodType,
            periodStart = request.periodStart,
            periodEnd = request.periodEnd,
            amount = request.amount,
            active = request.active,
        )
        val saved = budgetRepository.save(budget)
        auditService?.record(
            AuditRecord.currentActor(
                AuditAction.BUDGET_CREATED,
                "BUDGET",
                saved.id.toString(),
                newState = mapOf("periodType" to saved.periodType, "amount" to saved.amount, "active" to saved.active),
            ),
        )
        return saved
    }

    @Transactional
    fun update(id: Long, request: UpdateBudgetRequest): Budget {
        val budget = budgetRepository.findById(id).orElseThrow { IllegalArgumentException("Budget not found") }
        val before = budget.amount to budget.active
        request.amount?.let { amount ->
            require(amount >= BigDecimal.ZERO) { "amount must not be negative" }
            budget.amount = amount
        }
        request.active?.let { active -> budget.active = active }
        val updated = budgetRepository.save(budget)
        if (before != (updated.amount to updated.active)) {
            auditService?.record(
                AuditRecord.currentActor(
                    AuditAction.BUDGET_UPDATED,
                    "BUDGET",
                    updated.id.toString(),
                    previousState = mapOf("amount" to before.first, "active" to before.second),
                    newState = mapOf("amount" to updated.amount, "active" to updated.active),
                    changedFields = listOf("amount", "active"),
                ),
            )
        }
        return updated
    }

    @Transactional
    fun deactivate(id: Long): Budget {
        val budget = budgetRepository.findById(id).orElseThrow { IllegalArgumentException("Budget not found") }
        require(budget.active) { "Budget is already inactive" }
        if (budget.committedAmount > BigDecimal.ZERO) {
            throw IllegalStateException("Cannot deactivate a budget with active commitments")
        }
        budget.active = false
        val updated = budgetRepository.save(budget)
        auditService?.record(
            AuditRecord.currentActor(
                AuditAction.BUDGET_DEACTIVATED,
                "BUDGET",
                updated.id.toString(),
                previousState = mapOf("active" to true),
                newState = mapOf("active" to false),
                changedFields = listOf("active"),
            ),
        )
        return updated
    }

    @Transactional(readOnly = true)
    fun remaining(department: Department, date: LocalDate): BigDecimal {
        val budget = budgetRepository.findActiveBudgetForDate(requireNotNull(department.id), date).orElse(null) ?: return BigDecimal.ZERO
        return budget.amount - commitmentRepository.sumActiveAmountByBudgetId(budget.id)
    }

    @Transactional(readOnly = true)
    fun wouldExceed(department: Department, amount: BigDecimal, date: LocalDate): Boolean {
        val remaining = remaining(department, date)
        return amount > remaining
    }

    @Transactional
    fun commit(request: ResourceRequest, amount: BigDecimal, overrideComment: String? = null): BudgetCommitment =
        commitInternal(request.departmentId, request.requestId ?: 0, amount, request.requiredDate ?: LocalDate.now(), overrideComment)

    @Transactional
    fun commit(request: Any, amount: BigDecimal, overrideComment: String? = null): BudgetCommitment {
        val requestContext = resolveRequestContext(request)
        return commitInternal(requestContext.departmentId, requestContext.requestId, amount, requestContext.date, overrideComment)
    }

    @Transactional
    fun commit(departmentId: Int, requestId: Int, amount: BigDecimal, date: LocalDate, overrideComment: String? = null): BudgetCommitment =
        commitInternal(departmentId, requestId, amount, date, overrideComment)

    @Transactional
    fun release(request: ResourceRequest): BudgetCommitment? {
        val active = commitmentRepository.findAll().filter { it.requestId == (request.requestId ?: 0) && it.status == BudgetCommitmentStatus.ACTIVE }
        if (active.isEmpty()) return null
        val commitment = active.first()
        return release(commitment)
    }

    @Transactional
    fun release(commitment: BudgetCommitment): BudgetCommitment {
        if (commitment.status != BudgetCommitmentStatus.ACTIVE) return commitment
        commitment.status = BudgetCommitmentStatus.RELEASED
        commitment.budget.committedAmount = (commitment.budget.committedAmount - commitment.amount).coerceAtLeast(BigDecimal.ZERO)
        val saved = commitmentRepository.save(commitment)
        budgetRepository.save(commitment.budget)
        return saved
    }

    private fun commitInternal(departmentId: Int, requestId: Int, amount: BigDecimal, date: LocalDate, overrideComment: String?): BudgetCommitment {
        require(amount > BigDecimal.ZERO) { "Commitment amount must be positive" }
        val budget = budgetRepository.findActiveBudgetForDateForUpdate(departmentId, date)
            .or { budgetRepository.findActiveBudgetForDate(departmentId, date) }
            .orElseThrow {
                IllegalStateException("No active budget exists for department $departmentId on $date")
            }
        val existingCommitted = budget.committedAmount.max(commitmentRepository.sumActiveAmountByBudgetId(budget.id))
        val projectedCommitted = existingCommitted + amount
        val exceeded = projectedCommitted > budget.amount

        if (exceeded && (overrideComment == null || overrideComment.trim().length < 10)) {
            throw IllegalArgumentException("Override requires a reason with at least 10 characters")
        }
        if (exceeded) {
            auditService?.record(
                AuditRecord.currentActor(
                    AuditAction.BUDGET_OVERRIDE_USED,
                    "BUDGET",
                    budget.id.toString(),
                    previousState = mapOf("committedAmount" to existingCommitted),
                    newState = mapOf("committedAmount" to projectedCommitted),
                    comment = overrideComment,
                    changedFields = listOf("committedAmount"),
                ),
            )
        }

        val commitment = BudgetCommitment(
            budget = budget,
            requestId = requestId,
            amount = amount,
            status = BudgetCommitmentStatus.ACTIVE,
            comment = overrideComment,
        )
        budget.committedAmount = projectedCommitted
        val saved = commitmentRepository.save(commitment) ?: commitment
        budgetRepository.save(budget) ?: budget
        return saved
    }

    private fun resolveRequestContext(request: Any): RequestContext {
        return when (request) {
            is ResourceRequest -> RequestContext(request.departmentId, request.requestId ?: 0, request.requiredDate ?: LocalDate.now())
            is BudgetRequestStub -> {
                val stubDate = request.javaClass.methods.firstOrNull { it.name in setOf("date", "getDate") }?.invoke(request) as? LocalDate ?: LocalDate.now()
                RequestContext(request.department.id ?: 0, request.requestId, stubDate)
            }
            is BudgetRequest -> RequestContext(request.departmentId, request.requestId, request.date)
            else -> {
                val deptValue = findPropertyValue(request, "department", "departmentId")
                val idValue = findPropertyValue(request, "requestId", "id")
                val dateValue = findPropertyValue(request, "requiredDate", "date")
                val departmentId = when (deptValue) {
                    is Department -> deptValue.id ?: 0
                    is Number -> deptValue.toInt()
                    else -> 0
                }
                val requestId = when (idValue) {
                    is Number -> idValue.toInt()
                    else -> 0
                }
                val date = when (dateValue) {
                    is LocalDate -> dateValue
                    else -> LocalDate.now()
                }
                RequestContext(departmentId, requestId, date)
            }
        }
    }

    private fun findPropertyValue(target: Any, vararg names: String): Any? {
        val candidates = names.toSet() + names.map { "get${it.replaceFirstChar { it.uppercaseChar() }}" }
        return target.javaClass.methods.firstOrNull { it.name in candidates }
            ?.invoke(target)
    }

    private data class RequestContext(
        val departmentId: Int,
        val requestId: Int,
        val date: LocalDate,
    )
}

data class BudgetRequest(
    val requestId: Int,
    val departmentId: Int,
    val date: LocalDate,
)

class BudgetRequestStub(
    val requestId: Int,
    val department: Department,
)
