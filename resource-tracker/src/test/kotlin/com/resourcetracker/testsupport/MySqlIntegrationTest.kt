package com.resourcetracker.testsupport

import com.resourcetracker.auth.JwtService
import com.resourcetracker.department.Department
import com.resourcetracker.department.DepartmentRepository
import com.resourcetracker.request.repository.StockMovementRepository
import com.resourcetracker.resource.Resource
import com.resourcetracker.resource.ResourceCategory
import com.resourcetracker.resource.ResourceRepository
import com.resourcetracker.resource.ResourceStatus
import com.resourcetracker.resource.ResourceType
import com.resourcetracker.user.Role
import com.resourcetracker.user.User
import com.resourcetracker.user.UserRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mysql.MySQLContainer

@Testcontainers
@ActiveProfiles("test")
abstract class MySqlIntegrationTest {
    @Autowired
    protected lateinit var departments: DepartmentRepository

    @Autowired
    protected lateinit var users: UserRepository

    @Autowired
    protected lateinit var resources: ResourceRepository

    @Autowired
    protected lateinit var stockMovements: StockMovementRepository

    @Autowired
    protected lateinit var jwtService: JwtService

    protected fun tokenFor(role: Role, department: Department, username: String = role.name.lowercase()): String {
        val user = users.saveAndFlush(TestDataBuilders.user(role, department, username))
        return jwtService.issueAccessToken(user).token
    }

    companion object {
        @Container
        @JvmStatic
        val mysql = MySQLContainer("mysql:8.4").apply {
            withDatabaseName("resource_tracker_test")
            withUsername("resource_tracker")
            withPassword("resource_tracker")
        }

        @JvmStatic
        @DynamicPropertySource
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
            registry.add("app.security.jwt-secret") {
                "integration-test-secret-that-is-long-enough-for-hmac-sha256-123456"
            }
        }
    }
}

object TestDataBuilders {
    fun department(name: String) = Department(name = name, description = "$name integration-test department")

    fun user(role: Role, department: Department, username: String) = User(
        username = username,
        email = "$username@example.test",
        fullName = username,
        passwordHash = "not-used-by-jwt-tests",
        role = role,
        department = department,
    )

    fun internalResource(
        code: String,
        name: String,
        department: Department,
        quantity: Int,
    ) = Resource(
        code = code,
        name = name,
        type = ResourceType.INTERNAL,
        category = ResourceCategory.EQUIPMENT,
        unitOfMeasure = "unit",
        quantity = quantity,
        department = department,
        status = if (quantity > 0) ResourceStatus.AVAILABLE else ResourceStatus.OUT_OF_STOCK,
    )

    fun externalResource(code: String, name: String) = Resource(
        code = code,
        name = name,
        type = ResourceType.EXTERNAL,
        category = ResourceCategory.SOFTWARE,
        unitOfMeasure = "license",
    )
}

object JwtTestTokens {
    fun bearer(token: String): String = "Bearer $token"
}
