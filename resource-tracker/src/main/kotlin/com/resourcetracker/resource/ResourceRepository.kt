package com.resourcetracker.resource

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import com.resourcetracker.resource.ResourceCategory
import java.util.UUID

interface ResourceRepository : JpaRepository<Resource, UUID>, JpaSpecificationExecutor<Resource> {
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

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = ["department", "preferredVendor"])
    fun findWithRelationsById(id: UUID): Resource?

    fun existsByDepartmentIdAndActiveTrue(departmentId: Int): Boolean

    fun existsByPreferredVendorIdAndActiveTrue(vendorId: Int): Boolean

}
