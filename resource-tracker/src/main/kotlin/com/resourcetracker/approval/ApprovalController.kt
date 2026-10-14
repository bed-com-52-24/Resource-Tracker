package com.resourcetracker.approval

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/requests")
class ApprovalController(
    private val approvalService: ApprovalService,
    private val currentUser: CurrentUser
) {

    @PatchMapping("/{id}/approve")
    fun approve(
        @PathVariable id: Int,
        @Valid @RequestBody(required = false) body: DecisionRequest?
    ) = approvalService.approve(id, currentUser.id(), body?.comment)

    @PatchMapping("/{id}/reject")
    fun reject(
        @PathVariable id: Int,
        @Valid @RequestBody(required = false) body: DecisionRequest?
    ) = approvalService.reject(id, currentUser.id(), body?.comment)

    @PatchMapping("/{id}/query")
    fun query(
        @PathVariable id: Int,
        @Valid @RequestBody(required = false) body: DecisionRequest?
    ) = approvalService.query(id, currentUser.id(), body?.comment)
}