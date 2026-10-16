package com.resourcetracker.routing_rules

interface RoutingReferenceValidator {
    fun requireActiveDepartment(id: Int)
    fun requireActiveProject(id: Int)
    fun requireActiveTeam(id: Int)
    fun requireActiveDepartmentManager(id: Int)
}