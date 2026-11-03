package com.resourcetracker.audit

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Locale

@Service
class AuditEntryQueryService(private val entries: AuditEntryRepository) {
    @Transactional(readOnly = true)
    fun list(
        actorId: Int?,
        action: AuditAction?,
        itemType: String?,
        itemId: String?,
        from: Instant?,
        to: Instant?,
        page: Int,
        size: Int,
    ): Page<AuditEntryResponse> {
        if (actorId != null && actorId < 1) badRequest("actorId must be a positive integer")
        if (itemType != null && (itemType.isBlank() || itemType.length > 64)) badRequest("itemType must contain 1 to 64 characters")
        if (itemId != null && (itemId.isBlank() || itemId.length > 128)) badRequest("itemId must contain 1 to 128 characters")
        if (from != null && to != null && from.isAfter(to)) badRequest("from must be on or before to")
        if (page < 0) badRequest("page must be zero or greater")
        if (size !in 1..100) badRequest("size must be between 1 and 100")

        val specification = Specification<AuditEntry> { root, _, cb ->
            val predicates = mutableListOf<jakarta.persistence.criteria.Predicate>()
            actorId?.let { predicates += cb.equal(root.get<Int?>("actorId"), it) }
            action?.let { predicates += cb.equal(root.get<AuditAction>("action"), it) }
            itemType?.let { predicates += cb.equal(cb.lower(root.get<String>("itemType")), it.lowercase(Locale.ROOT)) }
            itemId?.let { predicates += cb.equal(root.get<String>("itemId"), it) }
            from?.let { predicates += cb.greaterThanOrEqualTo(root.get<Instant>("occurredAt"), it) }
            to?.let { predicates += cb.lessThanOrEqualTo(root.get<Instant>("occurredAt"), it) }
            cb.and(*predicates.toTypedArray())
        }
        val pageable = PageRequest.of(
            page,
            size,
            Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")),
        )
        return entries.findAll(specification, pageable).map { it.toResponse() }
    }

    private fun badRequest(message: String): Nothing =
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)
}
