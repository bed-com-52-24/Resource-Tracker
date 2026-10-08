package com.resourcetracker.department

import jakarta.persistence.*
import java.util.UUID

// TEMPORARY STUB for issue #16. Replace with the real entity when it is merged.
@Entity
@Table(name = "departments")
class Department(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(nullable = false, unique = true, length = 10)
    var code: String,

    @Column(nullable = false)
    var active: Boolean = true
)
