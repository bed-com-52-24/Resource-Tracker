package com.resourcetracker.department

import jakarta.persistence.*

@Entity
@Table(name = "departments")
class Department(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "department_id")
    val id: Int = 0,

    @Column(name = "department_name", nullable = false, length = 100, unique = true)
    var name: String,

    @Column(name = "description", length = 255)
    var description: String? = null,

    @Column(name = "status", length = 20)
    var status: String = "ACTIVE"
)
