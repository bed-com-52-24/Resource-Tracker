package com.resourcetracker.project

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

interface ProjectRepository : JpaRepository<Project, Int>, JpaSpecificationExecutor<Project> {
    fun existsByDepartmentIdAndNameIgnoreCase(departmentId: Int, name: String): Boolean
    fun existsByDepartmentIdAndNameIgnoreCaseAndIdNot(departmentId: Int, name: String, id: Int): Boolean
    fun existsByCodeIgnoreCase(code: String): Boolean
    fun existsByCodeIgnoreCaseAndIdNot(code: String, id: Int): Boolean
     fun existsByDepartmentIdAndActiveTrue(departmentId: Int): Boolean
}
