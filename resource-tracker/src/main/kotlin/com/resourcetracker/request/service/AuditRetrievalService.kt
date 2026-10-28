package com.resourcetracker.request.service

import com.resourcetracker.request.dto.RequestHistoryResponseDto
import com.resourcetracker.request.entity.RequestHistory
import com.resourcetracker.request.repository.RequestHistoryRepository
import com.resourcetracker.user.Role
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

@Service
class AuditRetrievalService(
    private val history: RequestHistoryRepository,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun byActor(
        actorId: Int,
        role: Role,
        from: LocalDate?,
        to: LocalDate?,
        pageable: Pageable,
    ): Page<RequestHistoryResponseDto> {
        requireManagingDirectorOrAdmin(role)
        if (actorId < 1) badRequest("actorId must be a positive integer")
        val range = dateRange(from, to, defaultLast30Days = true)
        return history.findAllByActorIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanEqualOrderByCreatedAtAscIdAsc(
            actorId,
            range.first.atStartOfDay(),
            range.second.atTime(LocalTime.MAX),
            pageable,
        ).map(::toResponse)
    }

    @Transactional(readOnly = true)
    fun byItem(itemType: String, itemId: Int, role: Role): List<RequestHistoryResponseDto> {
        if (role != Role.ADMIN) forbidden("Only Admin may retrieve audit history by item")
        if (itemId < 1) badRequest("itemId must be a positive integer")
        return when (itemType.uppercase(Locale.ROOT)) {
            "REQUEST" -> history.findAllByRequestIdOrderByCreatedAtAscIdAsc(itemId)
            "LINE_ITEM" -> history.findAllByLineItemIdOrderByCreatedAtAscIdAsc(itemId)
            else -> badRequest("Unsupported itemType '$itemType'. Supported types: REQUEST, LINE_ITEM")
        }.map(::toResponse)
    }

    @Transactional(readOnly = true)
    fun byActions(
        actions: Collection<String>,
        role: Role,
        from: LocalDate?,
        to: LocalDate?,
        pageable: Pageable,
    ): Page<RequestHistoryResponseDto> {
        requireManagingDirectorOrAdmin(role)
        val eventTypes = actions.map { it.trim().uppercase(Locale.ROOT) }.distinct()
        if (eventTypes.isEmpty() || eventTypes.any(String::isEmpty)) {
            badRequest("At least one non-empty action is required")
        }
        if (eventTypes.any { it.length > 30 }) badRequest("Action values must be 30 characters or less")
        val range = dateRange(from, to, defaultLast30Days = false)
        return history.findAllByEventTypeInAndCreatedAtGreaterThanEqualAndCreatedAtLessThanEqualOrderByCreatedAtAscIdAsc(
            eventTypes,
            range.first.atStartOfDay(),
            range.second.atTime(LocalTime.MAX),
            pageable,
        ).map(::toResponse)
    }

    private fun dateRange(from: LocalDate?, to: LocalDate?, defaultLast30Days: Boolean): Pair<LocalDate, LocalDate> {
        if (!defaultLast30Days && (from == null || to == null)) {
            badRequest("Both from and to dates are required")
        }
        val today = LocalDate.now(clock)
        val end = to ?: today
        val start = from ?: if (end.isBefore(LocalDate.MIN.plusDays(30))) {
            badRequest("to date is too early to apply the default date range")
        } else {
            end.minusDays(29)
        }
        if (start.isAfter(end)) badRequest("from date must be on or before to date")
        val yearDifference = end.year - start.year
        val exceedsOneYear = when {
            yearDifference > 1 -> true
            yearDifference < 1 -> false
            else -> start.plusYears(1).isBefore(end)
        }
        if (exceedsOneYear) badRequest("Date range cannot exceed one year")
        return start to end
    }

    private fun requireManagingDirectorOrAdmin(role: Role) {
        if (role !in setOf(Role.MANAGING_DIRECTOR, Role.ADMIN)) {
            forbidden("Only Managing Director or Admin may retrieve filtered audit history")
        }
    }

    private fun toResponse(entry: RequestHistory) = RequestHistoryResponseDto(
        entry.id,
        entry.requestId,
        entry.eventType,
        entry.fromStatus,
        entry.toStatus,
        entry.actorId,
        entry.lineItemId,
        entry.quantity,
        entry.comment,
        entry.createdAt,
    )

    private fun badRequest(message: String): Nothing =
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)

    private fun forbidden(message: String): Nothing =
        throw ResponseStatusException(HttpStatus.FORBIDDEN, message)
}
