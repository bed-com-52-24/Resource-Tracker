package com.resourcetracker.request.service

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.ConnectionCallback
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Year

@Service
class RequestNumberService(
    private val jdbcTemplate: JdbcTemplate,
    private val clock: Clock,
) {

    @Transactional
    fun generateRequestNumber(): String {
        val year = Year.now(clock).value
        repeat(5) {
            try {
                val nextSequence = jdbcTemplate.execute(ConnectionCallback { connection ->
                    connection.prepareStatement(
                        "SELECT last_sequence FROM resource_request_sequences WHERE request_year = ? FOR UPDATE",
                    ).use { select ->
                        select.setInt(1, year)
                        select.executeQuery().use { result ->
                            if (result.next()) {
                                val next = result.getInt(1) + 1
                                connection.prepareStatement(
                                    "UPDATE resource_request_sequences SET last_sequence = ? WHERE request_year = ?",
                                ).use { update ->
                                    update.setInt(1, next)
                                    update.setInt(2, year)
                                    update.executeUpdate()
                                }
                                next
                            } else {
                                connection.prepareStatement(
                                    "INSERT INTO resource_request_sequences (request_year, last_sequence) VALUES (?, 1)",
                                ).use { insert ->
                                    insert.setInt(1, year)
                                    insert.executeUpdate()
                                }
                                1
                            }
                        }
                    }
                }) ?: error("Could not allocate request number")

                require(nextSequence in 1..999999) {
                    "Request sequence exhausted for year $year"
                }
                return "REQ-$year-${nextSequence.toString().padStart(6, '0')}"
            } catch (error: DataIntegrityViolationException) {
                if (it == 4) throw error
            }
        }
        error("Could not allocate request number")
    }
}