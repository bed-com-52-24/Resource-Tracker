package com.resourcetracker.approval

import org.springframework.data.jpa.repository.JpaRepository

interface ApprovalAuditRepository : JpaRepository<ApprovalAudit, Int>