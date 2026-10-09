package com.resourcetracker.project

import com.resourcetracker.department.Department
import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(
    name = "projects",
    uniqueConstraints = [UniqueConstraint(columnNames = ["department_id", "name"])]
)
class Project(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(nullable = false, unique = true, length = 20)
    var code: String,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id")
    var department: Department,

    @Column(nullable = false)
    var startDate: LocalDate,

    var endDate: LocalDate? = null,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    var updatedAt: Instant = Instant.now()
)
