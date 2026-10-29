package com.resourcetracker.audit

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

interface AuditEntryRepository : JpaRepository<AuditEntry, Long>, JpaSpecificationExecutor<AuditEntry>
