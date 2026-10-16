package com.resourcetracker.request.service

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Year

@Service
class RequestNumberService(
    private val jdbcTemplate: JdbcTemplate
) {

    @Transactional
    fun generateRequestNumber(): String {
        val year = Year.now().value

        jdbcTemplate.update(
            """
            INSERT INTO resource_request_sequences
                (request_year, last_sequence)
            VALUES (?, LAST_INSERT_ID(1))
            ON DUPLICATE KEY UPDATE
                last_sequence = LAST_INSERT_ID(last_sequence + 1)
            """.trimIndent(),
            year
        )

        val sequence = jdbcTemplate.queryForObject(
            "SELECT LAST_INSERT_ID()",
            Long::class.java
        ) ?: error("Could not allocate request number")

        require(sequence in 1..999999) {
            "Request sequence exhausted for year $year"
        }

        return "REQ-$year-${sequence.toString().padStart(6, '0')}"
    }
}