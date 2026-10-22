package com.resourcetracker.request.service

import com.resourcetracker.request.dto.CreateResourceRequestDto
import com.resourcetracker.user.UserRepository
import com.resourcetracker.team.TeamRepository
import com.resourcetracker.common.BusinessRuleException
import com.resourcetracker.common.ResourceNotFoundException
import com.resourcetracker.security.AuthenticatedUser
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

data class RequestCreationResult(
    val httpStatus: Int,
    val responseBody: String
)

@Service
class RequestCreationFacade(
    private val idempotencyService: RequestIdempotencyService,
    private val requestService: ResourceRequestService,
    private val teams: TeamRepository,
    private val users: UserRepository
) {

    fun create(
        dto: CreateResourceRequestDto,
        requester: AuthenticatedUser,
        idempotencyKey: String
    ): RequestCreationResult {
        val team = teams.findById(dto.teamId).orElseThrow { ResourceNotFoundException("Team not found") }
        if (!team.isActive) throw BusinessRuleException("Team is inactive")
        val caller = users.findById(requester.id).orElseThrow { ResourceNotFoundException("User not found") }
        if (caller.teams.none { it.id == team.id }) throw com.resourcetracker.common.ApiException(
            HttpStatus.FORBIDDEN, "TEAM_FORBIDDEN", "You are not a member of this team"
        )
        idempotencyService.validateKey(idempotencyKey)

        val hash = idempotencyService.hashRequest(
            dto = dto,
            requesterId = requester.id,
        )

        val reservationId = try {
            idempotencyService.reserve(
                key = idempotencyKey,
                requesterId = requester.id,
                requestHash = hash
            )
        } catch (ex: IdempotencyConflict) {
            // A duplicate key may be a replay or an in-flight request.
            // inspect() distinguishes these cases.
            return idempotencyService.inspect(
                key = idempotencyKey,
                requesterId = requester.id,
                requestHash = hash
            )
        }

        return try {
            requestService.createRequestIdempotently(
                dto = dto,
                requesterId = requester.id,
                idempotencyId = reservationId
            )
        } catch (ex: Exception) {
            idempotencyService.fail(reservationId)
            throw ex
        }
    }
}
