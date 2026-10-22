package com.resourcetracker.team

import com.resourcetracker.user.Role
import com.resourcetracker.user.User
import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.domain.Specification

object TeamMemberSpecifications {
    fun filter(teamId: Int, role: Role?, active: Boolean?, search: String?): Specification<User> = Specification { root, query, cb ->
        query.distinct(true)
        val predicates = mutableListOf<Predicate>()
        val teamsJoin = root.join<Any, Any>("teams")
        predicates.add(cb.equal(teamsJoin.get<Int>("id"), teamId))
        role?.let { predicates.add(cb.equal(root.get<Role>("role"), it)) }
        active?.let { predicates.add(cb.equal(root.get<Boolean>("active"), it)) }
        search?.trim()?.takeIf { it.isNotEmpty() }?.let { term ->
            val like = "%${term.lowercase()}%"
            predicates.add(
                cb.or(
                    cb.like(cb.lower(root.get<String>("username")), like),
                    cb.like(cb.lower(root.get<String>("email")), like),
                    cb.like(cb.lower(root.get<String>("fullName")), like),
                )
            )
        }
        cb.and(*predicates.toTypedArray())
    }
}
