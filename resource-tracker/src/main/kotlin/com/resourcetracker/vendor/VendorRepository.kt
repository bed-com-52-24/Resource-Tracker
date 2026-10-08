package com.resourcetracker.vendor

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface VendorRepository : JpaRepository<Vendor, Int> {

    fun existsByNameIgnoreCase(name: String): Boolean

    @Query(
        """
        SELECT v FROM Vendor v
        WHERE (:status IS NULL OR UPPER(v.status) = UPPER(:status))
          AND (:keyword IS NULL OR LOWER(v.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
        """
    )
    fun search(
        @Param("status") status: String?,
        @Param("keyword") keyword: String?,
        pageable: Pageable
    ): Page<Vendor>
}