package com.resourcetracker.request.dto

import com.resourcetracker.request.entity.ActorType
import java.time.LocalDateTime

data class RequestHistoryResponseDto(
    val id: Int,
    val requestId: Int,
    val eventType: String,
    val fromStatus: String?,
    val toStatus: String?,
    val actorId: Int?,
    val lineItemId: Int?,
    val quantity: Int?,
    val comment: String?,
    val createdAt: LocalDateTime?,
    val actorType: ActorType = ActorType.USER,
)
