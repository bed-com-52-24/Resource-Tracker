package com.resourcetracker.approval

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

/** A reservation of stock for one approved internal line item. */
@Entity(name = "ResourceAllocation")
@Table(name = "resource_allocation")
class ResourceAllocation(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "allocation_id")
    val id: Int = 0,

    @Column(name = "request_id", nullable = false)
    val requestId: Int,

    @Column(name = "line_id", nullable = false)
    val lineId: Int,

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "resource_id", nullable = false)
    val resourceId: UUID,

    @Column(name = "quantity", nullable = false)
    val quantity: Int,

    /** RESERVED, or WAITLISTED (handed to RT-19). */
    @Column(name = "status", nullable = false, length = 20)
    var status: String
)