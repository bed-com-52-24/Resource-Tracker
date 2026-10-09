package com.resourcetracker.project

import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.domain.Specification
import java.time.LocalDate
import java.util.UUID

object ProjectSpecifications {

    fun filter(
        departmentId: Int?,
        active: Boolean?,
        startFrom: LocalDate?,
        startTo: LocalDate?,
        keyword: String?
    ): Specification<Project> = Specification { root, _, cb ->
        val predicates = mutableListOf<Predicate>()

        departmentId?.let { predicates.add(cb.equal(root.get<Any>("department").get<Int>("id"), it)) }
        active?.let { predicates.add(cb.equal(root.get<Boolean>("active"), it)) }
        startFrom?.let { predicates.add(cb.greaterThanOrEqualTo(root.get<LocalDate>("startDate"), it)) }
        startTo?.let { predicates.add(cb.lessThanOrEqualTo(root.get<LocalDate>("startDate"), it)) }

        keyword?.trim()?.takeIf { it.length >= 2 }?.let {
            val like = "%" + it.lowercase() + "%"
            predicates.add(
                cb.or(
                    cb.like(cb.lower(root.get<String>("name")), like),
                    cb.like(cb.lower(root.get<String>("code")), like)
                )
            )
        }

        cb.and(*predicates.toTypedArray())
    }
}
