package com.resourcetracker.resource

import com.resourcetracker.request.entity.RequestStatus
import com.resourcetracker.request.repository.RequestLineItemRepository
import com.resourcetracker.request.repository.StockIssueRepository
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.time.LocalDate

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class InventoryReportRepositoryIntegrationTest {
    @Autowired
    private lateinit var issues: StockIssueRepository

    @Autowired
    private lateinit var lines: RequestLineItemRepository

    @Autowired
    private lateinit var reconciliations: ResourceReconciliationRepository

    @Test
    fun `report queries run against the migrated inventory schema`() {
        assertTrue(issues.summarizeResources(listOf(1), LocalDate.now()).isEmpty())
        assertTrue(issues.findExpectedReturns(listOf(1), LocalDate.now()).isEmpty())
        assertTrue(
            lines.findApprovedWaitlist(
                ResourceType.INTERNAL,
                RequestStatus.APPROVED,
                null,
                PageRequest.of(0, 20),
            ).isEmpty,
        )
        assertTrue(reconciliations.findAllByResourceIdOrderByCreatedAtAscIdAsc(1).isEmpty())
    }

    companion object {
        @Container
        @JvmStatic
        val mysql = MySQLContainer<Nothing>("mysql:8.4").apply {
            withDatabaseName("resource_tracker")
            withUsername("test")
            withPassword("test")
            withStartupTimeout(Duration.ofMinutes(5))
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
            registry.add("spring.datasource.driver-class-name") { "com.mysql.cj.jdbc.Driver" }
        }
    }
}
