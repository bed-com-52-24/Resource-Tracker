package com.resourcetracker.report.repository

import com.resourcetracker.report.entity.DepartmentBudget
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface DepartmentBudgetRepository :
    JpaRepository<DepartmentBudget, Int> {

    fun findByDepartmentIdAndPeriodStartAndPeriodEnd(
        departmentId: Int,
        periodStart: LocalDate,
        periodEnd: LocalDate
    ): DepartmentBudget?

    @Query("""
        SELECT b
        FROM DepartmentBudget b
        WHERE b.periodStart <= :to
        AND b.periodEnd >= :from
    """)
    fun findOverlapping(
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate
    ): List<DepartmentBudget>
}