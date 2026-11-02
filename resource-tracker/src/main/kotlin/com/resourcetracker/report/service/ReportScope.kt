package com.resourcetracker.report.service

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

object ReportScope {

    private val allDepartmentRoles = setOf(
        "ADMIN",
        "MANAGING_DIRECTOR"
    )

    private val ownDepartmentRoles = setOf(
        "DEPARTMENT_MANAGER"
    )

    fun resolve(
        role: String,
        actorDepartmentId: Int?,
        requestedDepartmentId: Int?
    ): Int? {

        if (role in allDepartmentRoles) {
            return requestedDepartmentId
        }

        if (role in ownDepartmentRoles) {

            val ownDepartment = actorDepartmentId
                ?: forbidden()

            if (
                requestedDepartmentId != null &&
                requestedDepartmentId != ownDepartment
            ) {
                forbidden()
            }

            return ownDepartment
        }

        forbidden()
    }

    private fun forbidden(): Nothing {
        throw ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "You are not permitted to access this report"
        )
    }
}