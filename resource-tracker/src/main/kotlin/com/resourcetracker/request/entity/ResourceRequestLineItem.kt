package com.resourcetracker.request.entity

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "resource_request_line_items")
class ResourceRequestLineItem(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "line_item_id")
    var lineItemId: Int? = null,

    @Column(name = "request_id", nullable = false)
    var requestId: Int = 0,

    @Column(name = "description", nullable = false)
    var description: String = "",

    @Column(name = "manufacturer", nullable = false)
    var manufacturer: String = "",

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    var unitPrice: BigDecimal = BigDecimal.ZERO,

    @Column(name = "quantity", nullable = false)
    var quantity: Int = 1,

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    var category: RequestCategory = RequestCategory.OTHER,

    @Column(name = "batch_number", length = 50)
    var batchNumber: String? = null,

    @Column(name = "resource_id")
    var resourceId: Int? = null,

    @Column(name = "fulfillment_preference", nullable = false, length = 10)
    var fulfillmentPreference: String = "INTERNAL",

    @Column(name = "unit_of_measure", length = 50)
    var unitOfMeasure: String? = null,

    @Column(name = "approved_quantity")
    var approvedQuantity: Int? = null,

    @Column(name = "waived_quantity", nullable = false)
    var waivedQuantity: Int = 0,
)
