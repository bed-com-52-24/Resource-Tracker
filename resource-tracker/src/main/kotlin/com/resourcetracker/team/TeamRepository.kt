package com.resourcetracker.team

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

interface TeamRepository : JpaRepository<Team, Int>, JpaSpecificationExecutor<Team> {
    fun existsByDepartmentIdAndNameIgnoreCase(departmentId: Int, name: String): Boolean
    fun existsByDepartmentIdAndNameIgnoreCaseAndIdNot(departmentId: Int, name: String, id: Int): Boolean
    fun findByNameIgnoreCase(name: String): Team?
}
