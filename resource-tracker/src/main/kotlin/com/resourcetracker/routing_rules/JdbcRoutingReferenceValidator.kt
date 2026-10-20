package com.resourcetracker.routing_rules

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class JdbcRoutingReferenceValidator(
    private val jdbcTemplate: JdbcTemplate
) : RoutingReferenceValidator {

    override fun requireActiveDepartment(id: Int) {
        requireReference(
            "SELECT COUNT(*) FROM departments WHERE department_id = ? AND status = 'ACTIVE'",
            id,
            "Department is missing or inactive"
        )
    }

    override fun requireActiveProject(id: Int) {
        requireReference(
            "SELECT COUNT(*) FROM projects WHERE project_id = ? AND active = TRUE",
            id,
            "Project is missing or inactive"
        )
    }

    override fun requireActiveTeam(id: Int) {
        requireReference(
            "SELECT COUNT(*) FROM teams WHERE team_id = ? AND is_active = TRUE",
            id,
            "Team is missing or inactive"
        )
    }

    override fun requireActiveDepartmentManager(id: Int) {
        requireReference(
            "SELECT COUNT(*) FROM users WHERE user_id = ? AND active = TRUE AND role = 'DEPARTMENT_MANAGER'",
            id,
            "Manager is missing, inactive, or not a department manager"
        )
    }

    private fun requireReference(query: String, id: Int, message: String) {
        val matches = jdbcTemplate.queryForObject(query, Int::class.javaObjectType, id)
            ?: error("Reference validation query returned no result")
        if (matches == 0) throw ValidationException(message)
    }
}
