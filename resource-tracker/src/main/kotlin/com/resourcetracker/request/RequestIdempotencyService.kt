package com.resourcetracker.request.service

import com.resourcetracker.request.dto.CreateResourceRequestDto
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.HexFormat

data class IdempotencyRecord(
    val requesterId: Int,
    val requestHash: String,
    val status: String,
    val responseStatus: Int?,
    val responseBody: String?,
    val expiresAt: Instant
)

class IdempotencyConflict(message: String) :
    RuntimeException(message)

@Service
class RequestIdempotencyService(
    private val jdbcTemplate: JdbcTemplate,
    private val clock: Clock,
    private val objectMapper: ObjectMapper
) {

    fun validateKey(key: String?) {
        require(!key.isNullOrBlank() && key.length <= 64) {
            "Idempotency-Key must contain 1 to 64 characters"
        }
    }

    fun hashRequest(
        dto: CreateResourceRequestDto,
        requesterId: Int
    ): String {
        val payload = objectMapper.writeValueAsString(
            RequestHashPayload(dto, requesterId)
        )
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(StandardCharsets.UTF_8))

        return HexFormat.of().formatHex(digest)
    }

    private data class RequestHashPayload(
        val dto: CreateResourceRequestDto,
        val requesterId: Int
    )

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun reserve(
        key: String,
        requesterId: Int,
        requestHash: String
    ): Long {
        val now = clock.instant()
        val expiresAt = now.plus(Duration.ofMinutes(5))

        try {
            jdbcTemplate.update(
                """
                INSERT INTO resource_request_idempotency (
                    idempotency_key,
                    requester_id,
                    request_hash,
                    request_id,
                    processing,
                    status,
                    created_at,
                    updated_at,
                    expires_at
                ) VALUES (?, ?, ?, NULL, TRUE, 'IN_PROGRESS', ?, ?, ?)
                """.trimIndent(),
                key,
                requesterId,
                requestHash,
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(expiresAt)
            )
        } catch (ex: DuplicateKeyException) {
            throw IdempotencyConflict(
                "Idempotency key already exists"
            )
        }

        val id = jdbcTemplate.queryForObject(
            """
            SELECT idempotency_id
            FROM resource_request_idempotency
            WHERE idempotency_key = ?
            """.trimIndent(),
            Long::class.java,
            key
        ) ?: error("Idempotency reservation was not saved")

        audit(id, null, "IN_PROGRESS")
        return id
    }

    @Transactional(readOnly = true)
    fun inspect(
        key: String,
        requesterId: Int,
        requestHash: String
    ): RequestCreationResult {
        val records = jdbcTemplate.query(
            """
            SELECT requester_id, request_hash, status,
                   response_status, response_body, expires_at
            FROM resource_request_idempotency
            WHERE idempotency_key = ?
            """.trimIndent(),
            { rs, _ ->
                IdempotencyRecord(
                    requesterId = rs.getInt("requester_id"),
                    requestHash = rs.getString("request_hash"),
                    status = rs.getString("status"),
                    responseStatus =
                        rs.getObject("response_status") as? Int,
                    responseBody = rs.getString("response_body"),
                    expiresAt = rs.getTimestamp("expires_at").toInstant()
                )
            },
            key
        )

        val record = records.firstOrNull()
            ?: throw IdempotencyConflict(
                "Reservation no longer exists; retry the request"
            )

        if (
            record.requesterId != requesterId ||
            record.requestHash != requestHash
        ) {
            throw IdempotencyConflict(
                "Idempotency key was used for a different request"
            )
        }

        if (!clock.instant().isBefore(record.expiresAt)) {
            throw IdempotencyConflict("Idempotency key has expired")
        }

        if (record.status != "COMPLETED") {
            throw IdempotencyConflict(
                "Request with this key is still in progress"
            )
        }

        return RequestCreationResult(
            httpStatus = record.responseStatus
                ?: error("Completed record has no response status"),
            responseBody = record.responseBody
                ?: error("Completed record has no response body")
        )
    }

    @Transactional
    fun complete(
        idempotencyId: Long,
        requestId: Int,
        responseStatus: Int,
        responseBody: String
    ) {
        val updated = jdbcTemplate.update(
            """
            UPDATE resource_request_idempotency
            SET request_id = ?,
                processing = FALSE,
                status = 'COMPLETED',
                response_status = ?,
                response_body = ?,
                updated_at = ?
            WHERE idempotency_id = ?
              AND status = 'IN_PROGRESS'
              AND processing = TRUE
            """.trimIndent(),
            requestId,
            responseStatus,
            responseBody,
            java.sql.Timestamp.from(clock.instant()),
            idempotencyId
        )

        check(updated == 1) {
            "Idempotency record could not be completed"
        }

        audit(idempotencyId, "IN_PROGRESS", "COMPLETED")
    }

    @Transactional
    fun fail(idempotencyId: Long) {
        val updated = jdbcTemplate.update(
            """
            UPDATE resource_request_idempotency
            SET processing = FALSE,
                status = 'FAILED',
                updated_at = ?
            WHERE idempotency_id = ?
              AND status = 'IN_PROGRESS'
              AND processing = TRUE
            """.trimIndent(),
            java.sql.Timestamp.from(clock.instant()),
            idempotencyId
        )

        if (updated == 1) {
            audit(idempotencyId, "IN_PROGRESS", "FAILED")
        }
    }

    private fun audit(
        id: Long,
        previous: String?,
        next: String
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO resource_request_component_audit (
                idempotency_id, event_type, previous_state, new_state
            ) VALUES (?, 'IDEMPOTENCY_STATE_CHANGE', ?, ?)
            """.trimIndent(),
            id,
            previous,
            next
        )
    }
}
