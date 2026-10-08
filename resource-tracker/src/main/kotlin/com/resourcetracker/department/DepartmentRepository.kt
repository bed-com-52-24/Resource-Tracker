package com.resourcetracker.department

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

// TEMPORARY STUB for issue #16.
interface DepartmentRepository : JpaRepository<Department, UUID>
