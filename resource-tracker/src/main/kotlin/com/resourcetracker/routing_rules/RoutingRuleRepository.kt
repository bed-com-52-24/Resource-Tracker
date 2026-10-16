package com.resourcetracker.routing_rules

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface RoutingRuleRepository : JpaRepository<RoutingRule, Int> {

    @Query("""
        SELECT r FROM RoutingRule r
        WHERE r.deleted = false
        ORDER BY r.priority ASC, r.id ASC
    """)
    fun findAllNotDeleted(): List<RoutingRule>

    @Query("""
        SELECT r FROM RoutingRule r
        WHERE r.id = :id AND r.deleted = false
    """)
    fun findNotDeletedById(
        @Param("id") id: Int
    ): Optional<RoutingRule>

    @Query("""
        SELECT COUNT(r) > 0 FROM RoutingRule r
        WHERE r.departmentId = :departmentId
          AND (r.projectId = :projectId OR
               (r.projectId IS NULL AND :projectId IS NULL))
          AND (r.teamId = :teamId OR
               (r.teamId IS NULL AND :teamId IS NULL))
          AND r.active = true
          AND r.deleted = false
          AND (:excludeId IS NULL OR r.id <> :excludeId)
    """)
    fun existsActiveCombination(
        @Param("departmentId") departmentId: Int?,
        @Param("projectId") projectId: Int?,
        @Param("teamId") teamId: Int?,
        @Param("excludeId") excludeId: Int?
    ): Boolean
}