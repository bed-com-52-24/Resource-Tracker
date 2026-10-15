package com.resourcetracker.approval

import org.springframework.data.jpa.repository.JpaRepository

interface ApprovalRequestRepository : JpaRepository<ApprovalRequest, Int>