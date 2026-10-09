package com.resourcetracker.vendor

import jakarta.persistence.*

@Entity
@Table(name = "vendors")
class Vendor(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vendor_id")
    val id: Int = 0,

    @Column(name = "vendor_name", nullable = false, length = 150)
    var name: String,

    @Column(name = "contact_information", length = 255)
    var contactInformation: String? = null,

    @Column(name = "status", length = 20)
    var status: String = "ACTIVE"
)