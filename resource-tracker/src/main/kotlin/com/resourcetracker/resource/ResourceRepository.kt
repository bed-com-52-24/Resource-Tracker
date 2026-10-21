package com.resourcetracker.resource

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import com.resourcetracker.resource.ResourceCategory

interface ResourceRepository : JpaRepository<Resource, Int>, JpaSpecificationExecutor<Resource> {
    fun findByName(name: String): Resource?

    fun findByCode(code: String): Resource?

    fun existsByCode(code: String): Boolean

    fun existsByNameIgnoreCaseAndDepartmentId(name: String, departmentId: Int): Boolean

    fun existsByNameIgnoreCaseAndDepartmentIdAndIdNot(
        name: String,
        departmentId: Int,
        id: Int
    ): Boolean

    fun findByIdAndActiveTrue(id: Int): Resource?

    fun findAllByTypeAndActiveTrue(type: ResourceType, pageable: Pageable): Page<Resource>

    fun findAllByIdInAndActiveTrue(ids: Collection<Int>): List<Resource>

    @Query(
        """
        select r from Resource r
        left join fetch r.department
        left join fetch r.preferredVendor
        where r.id = :id
        """
    )
    fun findWithRelationsById(@Param("id") id: Int): Resource?

    fun existsByDepartmentIdAndActiveTrue(departmentId: Int): Boolean

    fun existsByPreferredVendorIdAndActiveTrue(vendorId: Int): Boolean

    @Query("""select r from Resource r left join fetch r.department
        where r.active = true and r.type = :type
        and (:departmentId is null or r.department.id = :departmentId)
        and (:category is null or r.category = :category)
        order by r.name""")
    fun findActiveForAvailability(
        @Param("type") type: ResourceType,
        @Param("departmentId") departmentId: Int?,
        @Param("category") category: ResourceCategory?,
    ): List<Resource>
}
