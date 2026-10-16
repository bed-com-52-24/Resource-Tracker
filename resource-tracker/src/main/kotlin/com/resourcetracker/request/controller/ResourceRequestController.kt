package com.resourcetracker.request.controller

import com.resourcetracker.request.dto.CreateResourceRequestDto
import com.resourcetracker.request.dto.UpdateResourceRequestDto
import com.resourcetracker.request.service.ResourceRequestService
import com.resourcetracker.common.UnauthenticatedException
import com.resourcetracker.security.AuthenticatedUser
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/requests")
class ResourceRequestController(
    private val requestService: ResourceRequestService
) {

    private fun currentUserId(
        authentication: Authentication
    ): Int =
        (authentication.principal as? AuthenticatedUser)?.id
            ?: throw UnauthenticatedException()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody dto: CreateResourceRequestDto,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        authentication: Authentication
    ) = requestService.createRequest(
        dto = dto,
        requesterId = currentUserId(authentication),
        assignedManagerId = TODO(
            "Resolve the manager using your routing rules"
        )
    )

    @GetMapping
    fun list(
        pageable: Pageable,
        authentication: Authentication
    ) = requestService.listMyRequests(
        requesterId = currentUserId(authentication),
        pageable = pageable
    )

    @PutMapping("/{id}")
    fun edit(
        @PathVariable id: Int,
        @Valid @RequestBody dto: UpdateResourceRequestDto,
        authentication: Authentication
    ) = requestService.editRequest(
        id,
        currentUserId(authentication),
        dto
    )

    @PostMapping("/{id}/resubmit")
    fun resubmit(
        @PathVariable id: Int,
        authentication: Authentication
    ) = requestService.resubmitRequest(
        id,
        currentUserId(authentication)
    )

    @PostMapping("/{id}/cancel")
    fun cancel(
        @PathVariable id: Int,
        authentication: Authentication
    ) = requestService.cancelRequest(
        id,
        currentUserId(authentication)
    )
}