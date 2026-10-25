package com.resourcetracker.request.service

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.ConnectionCallback
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Year

@Service
class RequestNumberService(
    private val jdbcTemplate: JdbcTemplate,
    private val clock: Clock
) {

    @Transactional
    fun generateRequestNumber(): String {
        val year = Year.now(clock).value

        val nextSequence = jdbcTemplate.execute(ConnectionCallback { connection ->
            connection.prepareStatement(
                """
                INSERT INTO resource_request_sequences (request_year, last_sequence)
                VALUES (?, LAST_INSERT_ID(1))
                ON DUPLICATE KEY UPDATE
                    last_sequence = LAST_INSERT_ID(last_sequence + 1)
                """.trimIndent()
            ).use { statement ->
                statement.setInt(1, year)
                statement.executeUpdate()
            }

            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT LAST_INSERT_ID()").use { result ->
                    check(result.next()) { "Could not allocate request number" }
                    result.getLong(1)
                }
            }
        }) ?: error("Could not allocate request number")

        require(nextSequence in 1..999999) {
            "Request sequence exhausted for year $year"
        }

        return "REQ-$year-${nextSequence.toString().padStart(6, '0')}"
    }
}