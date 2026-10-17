package com.example.resourcetracker.request

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Year

@Service
class RequestNumberGenerator(
    private val jdbcTemplate: JdbcTemplate,
    private val clock: Clock
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun nextNumber(): String {
        val year = Year.now(clock).value

        jdbcTemplate.update(
            """
            INSERT INTO resource_request_sequences
                (request_year, last_sequence)
            VALUES (?, 0)
            ON DUPLICATE KEY UPDATE
                request_year = VALUES(request_year)
            """.trimIndent(),
            year
        )

        val current = jdbcTemplate.queryForObject(
            """
            SELECT last_sequence
            FROM resource_request_sequences
            WHERE request_year = ?
            FOR UPDATE
            """.trimIndent(),
            Int::class.java,
            year
        ) ?: error("Could not read request sequence")

        check(current < 999999) {
            "Request number sequence exhausted for $year"
        }

        val next = current + 1

        jdbcTemplate.update(
            """
            UPDATE resource_request_sequences
            SET last_sequence = ?
            WHERE request_year = ?
            """.trimIndent(),
            next,
            year
        )

        return "REQ-%d-%06d".format(year, next)
    }
}