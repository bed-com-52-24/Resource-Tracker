package com.resourcetracker.report.repository

import com.resourcetracker.report.entity.DepartmentBudgetAudit
import org.springframework.data.jpa.repository.JpaRepository

interface DepartmentBudgetAuditRepository :
    JpaRepository<DepartmentBudgetAudit, Int>