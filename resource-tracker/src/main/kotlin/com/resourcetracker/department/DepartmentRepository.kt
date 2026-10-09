package com.resourcetracker.department

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface DepartmentRepository : JpaRepository<Department, Int> {

    fun existsByNameIgnoreCase(name: String): Boolean

    @Query(
        """
        SELECT d FROM Department d
        WHERE (:status IS NULL OR UPPER(d.status) = UPPER(:status))
          AND (:keyword IS NULL OR LOWER(d.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
        """
    )
    fun search(
        @Param("status") status: String?,
        @Param("keyword") keyword: String?,
        pageable: Pageable
    ): Page<Department>
}
