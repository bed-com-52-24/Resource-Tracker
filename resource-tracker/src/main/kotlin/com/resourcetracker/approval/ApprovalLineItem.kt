package com.resourcetracker.approval

import jakarta.persistence.*

/** One item inside a request. Each item is approved or rejected separately. */
@Entity(name = "ApprovalLineItem")
@Table(name = "request_line_items")
class ApprovalLineItem(

    @Id
    @Column(name = "line_id")
    val id: Int = 0,

    @Column(name = "request_id", nullable = false)
    val requestId: Int,

    @Column(name = "item_description", nullable = false, length = 255)
    val description: String,

    @Column(name = "quantity", nullable = false)
    val quantity: Int,

    /** INTERNAL (from our own stock) or EXTERNAL (bought from a vendor). */
    @Column(name = "source", nullable = false, length = 20)
    val source: String,

    @Column(name = "status", nullable = false, length = 20)
    var status: String,

    @Column(name = "decision_comment", length = 500)
    var decisionComment: String?
)