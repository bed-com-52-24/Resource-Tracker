package com.resourcetracker.resource

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ResourceRepository : JpaRepository<Resource, UUID> {
    fun findByName(name: String): Resource?

    fun findByCode(code: String): Resource?

    fun existsByCode(code: String): Boolean

    fun existsByNameIgnoreCaseAndDepartmentId(name: String, departmentId: Int): Boolean

    fun existsByNameIgnoreCaseAndDepartmentIdAndIdNot(
        name: String,
        departmentId: Int,
        id: UUID
    ): Boolean

    fun findByIdAndActiveTrue(id: UUID): Resource?

    fun findAllByTypeAndActiveTrue(type: ResourceType, pageable: Pageable): Page<Resource>

    fun findAllByIdInAndActiveTrue(ids: Collection<UUID>): List<Resource>

    @Query(
        """
        select r from Resource r
        left join fetch r.department
        left join fetch r.preferredVendor
        where r.id = :id
        """
    )
    fun findWithRelationsById(@Param("id") id: UUID): Resource?

    fun existsByDepartmentIdAndActiveTrue(departmentId: Int): Boolean

    fun existsByPreferredVendorIdAndActiveTrue(vendorId: Int): Boolean
}
