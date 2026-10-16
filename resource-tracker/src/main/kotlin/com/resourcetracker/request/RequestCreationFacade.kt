package com.resourcetracker.request.service

import com.resourcetracker.request.dto.CreateResourceRequestDto
import org.springframework.stereotype.Service

data class RequestCreationResult(
    val httpStatus: Int,
    val responseBody: String
)

@Service
class RequestCreationFacade(
    private val idempotencyService: RequestIdempotencyService,
    private val requestService: ResourceRequestService
) {

    fun create(
        dto: CreateResourceRequestDto,
        requesterId: Int,
        assignedManagerId: Int,
        idempotencyKey: String
    ): RequestCreationResult {
        idempotencyService.validateKey(idempotencyKey)

        val hash = idempotencyService.hashRequest(
            dto = dto,
            requesterId = requesterId,
            assignedManagerId = assignedManagerId
        )

        val reservationId = try {
            idempotencyService.reserve(
                key = idempotencyKey,
                requesterId = requesterId,
                requestHash = hash
            )
        } catch (ex: IdempotencyConflict) {
            // A duplicate key may be a replay or an in-flight request.
            // inspect() distinguishes these cases.
            return idempotencyService.inspect(
                key = idempotencyKey,
                requesterId = requesterId,
                requestHash = hash
            )
        }

        return try {
            requestService.createRequestIdempotently(
                dto = dto,
                requesterId = requesterId,
                assignedManagerId = assignedManagerId,
                idempotencyId = reservationId
            )
        } catch (ex: Exception) {
            idempotencyService.fail(reservationId)
            throw ex
        }
    }
}