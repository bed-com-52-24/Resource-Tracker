package com.resourcetracker.request.repository

import com.resourcetracker.request.entity.BudgetOverride
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import jakarta.persistence.LockModeType

interface BudgetOverrideRepository : JpaRepository<BudgetOverride, Int> {
    fun findAllByRequestIdAndStatus(requestId: Int, status: com.resourcetracker.request.entity.BudgetOverrideStatus): List<BudgetOverride>
    fun findAllByStatusOrderByCreatedAtAsc(status: com.resourcetracker.request.entity.BudgetOverrideStatus): List<BudgetOverride>
    fun findFirstByLineItemIdOrderByIdDesc(lineItemId: Int): BudgetOverride?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from BudgetOverride o where o.id = :id")
    fun findForUpdateById(@Param("id") id: Int): BudgetOverride?
}
