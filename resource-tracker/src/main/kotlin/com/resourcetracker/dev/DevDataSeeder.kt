package com.resourcetracker.dev

import com.resourcetracker.department.Department
import com.resourcetracker.department.DepartmentRepository
import com.resourcetracker.user.Role
import com.resourcetracker.user.CreateUserRequest
import com.resourcetracker.user.UserRepository
import com.resourcetracker.user.UserService
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/** for local development */

@Component
@Profile("dev")
class DevDataSeeder(private val users: UserRepository,
private val userService: UserService,
private val departments: DepartmentRepository): ApplicationRunner
{
    override fun run(args: ApplicationArguments){
        if (!users.existsByUsernameIgnoreCase("admin")) {
            val department = departments.findByNameIgnoreCase("IT")
                ?: departments.save(Department(name = "IT"))
            userService.create(
                CreateUserRequest(
                    username = "admin",
                    email = "admin@example.com",
                    fullName = "Dev Admin",
                    password = "Admin@2026",
                    role = Role.ADMIN,
                    departmentId = department.id,
                    teamIds = emptySet(),
                ),
            )
        }
    }
}
