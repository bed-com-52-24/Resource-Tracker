package com.resourcetracker.project

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.util.UUID

interface ProjectRepository : JpaRepository<Project, UUID>, JpaSpecificationExecutor<Project> {
    fun existsByDepartmentIdAndNameIgnoreCase(departmentId: Int, name: String): Boolean
    fun existsByDepartmentIdAndNameIgnoreCaseAndIdNot(departmentId: Int, name: String, id: UUID): Boolean
    fun existsByCodeIgnoreCase(code: String): Boolean
    fun existsByCodeIgnoreCaseAndIdNot(code: String, id: UUID): Boolean
     fun existsByDepartmentIdAndActiveTrue(departmentId: Int): Boolean
}
