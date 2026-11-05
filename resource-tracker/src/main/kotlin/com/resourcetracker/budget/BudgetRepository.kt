package com.resourcetracker.budget

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.util.Optional

interface BudgetRepository : JpaRepository<Budget, Long> {
    fun findAllByDepartment_Id(departmentId: Int): List<Budget>
    fun existsByDepartmentIdAndActiveTrue(departmentId: Int): Boolean
    @Query(
        "select b from Budget b where b.department.id = :departmentId and b.active = true and b.periodStart <= :date and b.periodEnd >= :date",
    )
    fun findActiveBudgetForDate(@Param("departmentId") departmentId: Int, @Param("date") date: LocalDate): Optional<Budget>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "select b from Budget b where b.department.id = :departmentId and b.active = true and b.periodStart <= :date and b.periodEnd >= :date",
    )
    fun findActiveBudgetForDateForUpdate(@Param("departmentId") departmentId: Int, @Param("date") date: LocalDate): Optional<Budget>

    @Query(
        "select case when count(b) > 0 then true else false end from Budget b " +
            "where b.department.id = :departmentId and b.active = true and b.periodType = :periodType " +
            "and b.periodStart <= :periodEnd and b.periodEnd >= :periodStart",
    )
    fun existsActiveOverlap(
        @Param("departmentId") departmentId: Int,
        @Param("periodType") periodType: BudgetPeriodType,
        @Param("periodStart") periodStart: LocalDate,
        @Param("periodEnd") periodEnd: LocalDate,
    ): Boolean
}

interface BudgetCommitmentRepository : JpaRepository<BudgetCommitment, Long> {
    @Query("select coalesce(sum(c.amount), 0) from BudgetCommitment c where c.budget.id = :budgetId and c.status = 'ACTIVE'")
    fun sumActiveAmountByBudgetId(@Param("budgetId") budgetId: Long): java.math.BigDecimal

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from BudgetCommitment c where c.budget.id = :budgetId and c.requestId = :requestId and c.status = 'ACTIVE'")
    fun findActiveByBudgetAndRequestForUpdate(@Param("budgetId") budgetId: Long, @Param("requestId") requestId: Int): List<BudgetCommitment>
}
