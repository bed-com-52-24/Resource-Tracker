package com.resourcetracker.dev

import com.resourcetracker.department.Department
import com.resourcetracker.department.DepartmentRepository
import com.resourcetracker.user.Role
import com.resourcetracker.user.User
import com.resourcetracker.user.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/** for local development */

@Component
@Profile("dev")
class DevDataSeeder(private val users: UserRepository,
private val departments: DepartmentRepository,
private val encoder: PasswordEncoder): ApplicationRunner
{
    override fun run(args: ApplicationArguments){
        if (!users.existsByUsernameIgnoreCase("admin")) {
            val department = departments.findByNameIgnoreCase("IT")
                ?: departments.save(Department(name = "IT"))
            users.save(
                User(
                    username = "admin",
                    email = "admin@example.com",
                    fullName = "Dev Admin",
                    passwordHash = encoder.encode("Admin@2026")!!,
                    role = Role.ADMIN,
                    department = department,
                )
            )
        }
    }
}
