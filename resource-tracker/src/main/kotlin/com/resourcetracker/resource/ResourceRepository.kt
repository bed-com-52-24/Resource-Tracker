package com.resourcetracker.resource

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import jakarta.persistence.LockModeType
import com.resourcetracker.resource.ResourceCategory

interface ResourceRepository : JpaRepository<Resource, Int>, JpaSpecificationExecutor<Resource> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Resource r where r.id = :id")
    fun findForUpdateById(@Param("id") id: Int): Resource?
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

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = ["department", "preferredVendor"])
    fun findWithRelationsById(id: Int): Resource?

    fun existsByDepartmentIdAndActiveTrue(departmentId: Int): Boolean

    fun existsByPreferredVendorIdAndActiveTrue(vendorId: Int): Boolean

    @Query(
        """
        select count(r) as resourceCount,
               coalesce(sum(coalesce(r.quantity, 0)), 0) as onHandQuantity,
               coalesce(sum(r.unavailableQuantity), 0) as unavailableQuantity
        from Resource r
        where r.type = com.resourcetracker.resource.ResourceType.INTERNAL
          and (:departmentId is null or r.department.id = :departmentId)
        """,
    )
    fun summarizeInternalStock(@Param("departmentId") departmentId: Int?): InternalStockTotals

}

interface InternalStockTotals {
    fun getResourceCount(): Long
    fun getOnHandQuantity(): Long
    fun getUnavailableQuantity(): Long
}
