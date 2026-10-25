package com.resourcetracker.request.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(properties = ["app.security.jwt-secret=resource-tracker-request-number-test-secret-32"])
@Testcontainers(disabledWithoutDocker = true)
class RequestNumberServiceTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    companion object {
        @Container
        @JvmStatic
        val mysql = MySQLContainer<Nothing>("mysql:8.4").apply {
            withDatabaseName("resource_tracker")
            withUsername("test")
            withPassword("test")
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
            registry.add("spring.datasource.driver-class-name") {
                "com.mysql.cj.jdbc.Driver"
            }
        }
    }

    @BeforeEach
    fun cleanSequenceTable() {
        jdbcTemplate.update("DELETE FROM resource_request_sequences")
    }

    @Test
    fun `generates first request number`() {
        val service = serviceAt("2026-06-15T10:00:00Z")

        assertEquals(
            "REQ-2026-000001",
            service.generateRequestNumber()
        )
    }

    @Test
    fun `generates sequential unique request numbers`() {
        val service = serviceAt("2026-06-15T10:00:00Z")

        val first = service.generateRequestNumber()
        val second = service.generateRequestNumber()
        val third = service.generateRequestNumber()

        assertEquals("REQ-2026-000001", first)
        assertEquals("REQ-2026-000002", second)
        assertEquals("REQ-2026-000003", third)
        assertEquals(3, sequenceForYear(2026))
    }

    @Test
    fun `resets sequence when year changes`() {
        val service2026 = serviceAt("2026-12-31T23:59:59Z")
        val service2027 = serviceAt("2027-01-01T00:00:00Z")

        assertEquals(
            "REQ-2026-000001",
            service2026.generateRequestNumber()
        )
        assertEquals(
            "REQ-2026-000002",
            service2026.generateRequestNumber()
        )

        assertEquals(
            "REQ-2027-000001",
            service2027.generateRequestNumber()
        )

        // The old year's sequence is retained.
        assertEquals(2, sequenceForYear(2026))
        assertEquals(1, sequenceForYear(2027))
    }

    @Test
    fun `50 parallel allocations produce unique request numbers`() {
        val service = serviceAt("2026-06-15T10:00:00Z")
        val threadCount = 50
        val executor = Executors.newFixedThreadPool(threadCount)

        try {
            val tasks = (1..threadCount).map {
                Callable {
                    service.generateRequestNumber()
                }
            }

            val futures = executor.invokeAll(
                tasks,
                60,
                TimeUnit.SECONDS
            )

            val numbers = futures.map { future ->
                future.get(60, TimeUnit.SECONDS)
            }

            assertEquals(threadCount, numbers.size)
            assertEquals(threadCount, numbers.toSet().size)

            val expected = (1..threadCount)
                .map { "REQ-2026-${it.toString().padStart(6, '0')}" }
                .toSet()

            assertEquals(expected, numbers.toSet())
            assertEquals(threadCount, sequenceForYear(2026))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `old year numbers are not reused`() {
        val service2026 = serviceAt("2026-12-31T23:59:59Z")
        val service2027 = serviceAt("2027-01-01T00:00:00Z")

        val oldNumber = service2026.generateRequestNumber()
        val newNumber = service2027.generateRequestNumber()

        assertNotEquals(oldNumber, newNumber)
        assertEquals("REQ-2026-000001", oldNumber)
        assertEquals("REQ-2027-000001", newNumber)
    }

    private fun serviceAt(instant: String): RequestNumberService {
        val clock = Clock.fixed(
            Instant.parse(instant),
            ZoneOffset.UTC
        )

        return RequestNumberService(jdbcTemplate, clock)
    }

    private fun sequenceForYear(year: Int): Int =
        jdbcTemplate.queryForObject(
            """
            SELECT last_sequence
            FROM resource_request_sequences
            WHERE request_year = ?
            """.trimIndent(),
            Int::class.java,
            year
        ) ?: 0
}