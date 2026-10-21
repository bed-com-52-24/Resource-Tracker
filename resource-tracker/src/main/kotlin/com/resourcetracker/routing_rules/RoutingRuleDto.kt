package com.resourcetracker.routing_rules

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

data class RoutingRuleRequest(
    @field:NotNull
    val departmentId: Int?,

    val projectId: Int? = null,
    val teamId: Int? = null,

    @field:NotNull
    val managerId: Int?,

    @field:NotNull
    @field:Min(0)
    val priority: Int?
)

data class UpdateRoutingRuleRequest(
    @field:NotNull
    val departmentId: Int?,

    val projectId: Int? = null,
    val teamId: Int? = null,

    @field:NotNull
    val managerId: Int?,

    @field:NotNull
    @field:Min(0)
    val priority: Int?,

    val active: Boolean? = null,
)

data class RoutingRuleResponse(
    val id: Int,
    val departmentId: Int,
    val projectId: Int?,
    val teamId: Int?,
    val managerId: Int,
    val priority: Int,
    val active: Boolean,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?
) {
    companion object {
        fun from(rule: RoutingRule) = RoutingRuleResponse(
            id = requireNotNull(rule.id),
            departmentId = rule.departmentId ?: 0,
            projectId = rule.projectId,
            teamId = rule.teamId,
            managerId = rule.managerId,
            priority = rule.priority,
            active = rule.active,
            createdAt = rule.createdAt,
            updatedAt = rule.updatedAt
        )
    }
}
