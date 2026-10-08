package com.resourcetracker.department

import org.springframework.data.jpa.repository.JpaRepository

interface DepartmentRepository : JpaRepository<Department, Int> {
    fun existsByNameIgnoreCase(name: String): Boolean
}