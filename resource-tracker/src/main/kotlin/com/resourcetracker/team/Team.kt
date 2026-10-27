package com.resourcetracker.team

import com.resourcetracker.department.Department
import com.resourcetracker.project.Project
import com.resourcetracker.user.User
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "teams")
class Team(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "team_id")
    val id: Int = 0,

    @Column(name = "team_name", nullable = false, length = 100)
    var name: String,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id")
    var department: Department,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    var manager: User? = null,

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "team_projects",
        joinColumns = [JoinColumn(name = "team_id")],
        inverseJoinColumns = [JoinColumn(name = "project_id")]
    )
    var projects: MutableSet<Project> = mutableSetOf(),

    @ManyToMany(mappedBy = "teams")
    var users: MutableSet<User> = mutableSetOf(),

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
