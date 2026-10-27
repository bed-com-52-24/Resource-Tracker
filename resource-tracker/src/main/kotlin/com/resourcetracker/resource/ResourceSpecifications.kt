package com.resourcetracker.resource

import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.JoinType
import com.resourcetracker.department.Department
import org.springframework.data.jpa.domain.Specification

object ResourceSpecifications {

    fun filter(
        active: Boolean?,
        type: ResourceType?,
        category: ResourceCategory?,
        status: ResourceStatus?,
        departmentId: Int?,
        keyword: String?,
        scopeDepartmentId: Int? = null,
    ): Specification<Resource> = Specification { root, _, cb ->
        val predicates = mutableListOf<Predicate>()

        active?.let { predicates.add(cb.equal(root.get<Boolean>("active"), it)) }
        type?.let { predicates.add(cb.equal(root.get<ResourceType>("type"), it)) }
        category?.let { predicates.add(cb.equal(root.get<ResourceCategory>("category"), it)) }
        status?.let { predicates.add(cb.equal(root.get<ResourceStatus>("status"), it)) }
        departmentId?.let { predicates.add(cb.equal(root.get<Int>("department").get<Int>("id"), it)) }
        scopeDepartmentId?.let { scope ->
            val department = root.join<Resource, Department>("department", JoinType.LEFT)
            predicates.add(
                cb.or(
                    cb.equal(root.get<ResourceType>("type"), ResourceType.EXTERNAL),
                    cb.equal(department.get<Int>("id"), scope),
                ),
            )
        }

        keyword?.trim()?.takeIf { it.isNotEmpty() }?.let {
            val like = "%${it.lowercase()}%"
            predicates.add(
                cb.or(
                    cb.like(cb.lower(root.get<String>("name")), like),
                    cb.like(cb.lower(root.get<String>("code")), like),
                )
            )
        }

        cb.and(*predicates.toTypedArray())
    }
}
