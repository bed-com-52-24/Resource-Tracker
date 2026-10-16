package com.resourcetracker.routing_rules

import org.springframework.data.jpa.repository.JpaRepository

interface AuditEntryRepository : JpaRepository<AuditEntry, Long>