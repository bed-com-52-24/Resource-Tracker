package com.resourcetracker.resource

import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.domain.Specification

object ResourceSpecifications {

    fun filter(
        active: Boolean?,
        type: ResourceType?,
        category: ResourceCategory?,
        status: ResourceStatus?,
        departmentId: Int?,
        keyword: String?,
    ): Specification<Resource> = Specification { root, _, cb ->
        val predicates = mutableListOf<Predicate>()

        active?.let { predicates.add(cb.equal(root.get<Boolean>("active"), it)) }
        type?.let { predicates.add(cb.equal(root.get<ResourceType>("type"), it)) }
        category?.let { predicates.add(cb.equal(root.get<ResourceCategory>("category"), it)) }
        status?.let { predicates.add(cb.equal(root.get<ResourceStatus>("status"), it)) }
        departmentId?.let { predicates.add(cb.equal(root.get<Int>("department").get<Int>("id"), it)) }

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
