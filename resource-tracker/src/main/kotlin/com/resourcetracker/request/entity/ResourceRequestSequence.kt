package com.resourcetracker.request.entity

import jakarta.persistence.*

@Entity
@Table(name = "resource_request_sequences")
class ResourceRequestSequence(

    @Id
    @Column(name = "request_year")
    var requestYear: Int = 0,

    @Column(name = "last_sequence", nullable = false)
    var lastSequence: Int = 0
)