package com.resourcetracker.department

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface DepartmentRepository : JpaRepository<Department, Int> {

    fun existsByNameIgnoreCase(name: String): Boolean
    fun findByNameIgnoreCase(name: String): Department?

    @Query(
        """
        SELECT d FROM Department d
        WHERE (:active IS NULL OR d.status = CASE WHEN :active = true THEN 'ACTIVE' ELSE 'INACTIVE' END)
          AND (:keyword IS NULL OR LOWER(d.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
        """
    )
    fun search(
        @Param("active") active: Boolean?,
        @Param("keyword") keyword: String?,
        pageable: Pageable
    ): Page<Department>
}
