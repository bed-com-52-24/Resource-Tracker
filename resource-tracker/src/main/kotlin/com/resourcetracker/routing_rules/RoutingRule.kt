
package com.resourcetracker.routing_rules

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "routing_rules")
class RoutingRule(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "routing_rule_id")
    var id: Int? = null,

    @Column(name = "department_id")
    var departmentId: Int? = null,

    @Column(name = "project_id")
    var projectId: Int? = null,

    @Column(name = "team_id")
    var teamId: Int? = null,

    @Column(name = "line_manager_id", nullable = false)
    var managerId: Int = 0,

    @Column(nullable = false)
    var priority: Int = 0,

    @Column(nullable = false)
    var active: Boolean = false,

    @Column(nullable = false)
    var deleted: Boolean = false,

    @Column(name = "created_by")
    var createdBy: String = "",

    @Column(name = "updated_by")
    var updatedBy: String = "",

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null,

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: LocalDateTime? = null,

    @Column(name = "updated_at", insertable = false, updatable = false)
    var updatedAt: LocalDateTime? = null
)
