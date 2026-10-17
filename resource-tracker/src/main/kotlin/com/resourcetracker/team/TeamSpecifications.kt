package com.resourcetracker.team

import com.resourcetracker.project.Project
import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.domain.Specification
import java.util.UUID

object TeamSpecifications {

    fun filter(
        departmentId: Int?,
        managerId: Int?,
        projectId: UUID?,
        status: String?,
        keyword: String?
    ): Specification<Team> = Specification { root, query, cb ->
        val predicates = mutableListOf<Predicate>()

        departmentId?.let { predicates.add(cb.equal(root.get<Any>("department").get<Int>("id"), it)) }
        managerId?.let { predicates.add(cb.equal(root.get<Any>("manager").get<Int>("id"), it)) }
        projectId?.let {
            query.distinct(true)
            predicates.add(cb.equal(root.join<Team, Project>("projects").get<UUID>("id"), it))
        }
        status?.let { predicates.add(cb.equal(cb.upper(root.get<String>("status")), it.uppercase())) }

        keyword?.trim()?.takeIf { it.length >= 2 }?.let {
            predicates.add(cb.like(cb.lower(root.get<String>("name")), "%" + it.lowercase() + "%"))
        }

        cb.and(*predicates.toTypedArray())
    }
}
