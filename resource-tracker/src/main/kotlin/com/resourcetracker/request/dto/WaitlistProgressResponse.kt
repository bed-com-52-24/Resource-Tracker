package com.resourcetracker.request.dto

import com.resourcetracker.request.entity.WaitlistStatus

data class WaitlistProgressResponse(
	val id: Int,
	val status: WaitlistStatus,
	val quantity: Int,
	val reservedQuantity: Int,
	val issuedQuantity: Int,
	val position: Int?,
)
