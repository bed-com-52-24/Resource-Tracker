package com.resourcetracker.dev

import com.resourcetracker.department.Department
import com.resourcetracker.department.DepartmentRepository
import com.resourcetracker.project.Project
import com.resourcetracker.project.ProjectRepository
import com.resourcetracker.team.Team
import com.resourcetracker.team.TeamRepository
import com.resourcetracker.user.Role
import com.resourcetracker.user.User
import com.resourcetracker.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * Seeds sample data when the dev or prod profile is active.
 *
 * Safe to run on every startup: each item is skipped if it already exists.
 * Users, departments and teams mirror the payloads used by the corresponding APIs.
 */
@Component
@Profile("dev | prod")
class DevDataSeeder(
    private val users: UserRepository,
    private val departments: DepartmentRepository,
    private val encoder: PasswordEncoder,
    private val projectRepository: ProjectRepository,
    private val teamRepository: TeamRepository,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(DevDataSeeder::class.java)
    private val devPasswordHash by lazy { encoder.encode(DEV_PASSWORD)!! }
    private val adminPasswordHash by lazy { encoder.encode(ADMIN_PASSWORD)!! }

    override fun run(args: ApplicationArguments) {
        seedDepartments()
        seedUsers(LEADERSHIP + listOf(ADMIN))
        seedProjects()
        seedTeams()
        seedUsers(TEAM_USERS)
        log.info("Dev seed data ready. Log in as 'admin' / '$ADMIN_PASSWORD', or any other seeded user / '$DEV_PASSWORD'.")
    }

    private fun seedDepartments() {
        DEPARTMENTS.forEach { (name, description) ->
            val existing = departments.findByNameIgnoreCase(name)
            when {
                existing == null -> departments.save(Department(name = name, description = description))
                existing.description == null -> {
                    // "IT" is created by seedUser() without a description.
                    existing.description = description
                    departments.save(existing)
                }
            }
        }
    }

    private fun seedTeams() {
        val projectsByCode = projectRepository.findAll().associateBy { it.code }
        TEAMS.forEach { t ->
            val dept = department(t.department)
            if (teamRepository.existsByDepartmentIdAndNameIgnoreCase(dept.id, t.name)) return@forEach
            val manager = users.findByUsername(t.managerUsername)
                ?: error("Seed manager '${t.managerUsername}' is missing")
            teamRepository.save(
                Team(
                    name = t.name,
                    department = dept,
                    manager = manager,
                    projects = t.projectCodes.map { code ->
                        projectsByCode[code] ?: error("Seed project '$code' is missing")
                    }.toMutableSet(),
                )
            )
        }
    }

    private fun seedProjects() {
        PROJECTS.forEach { seed ->
            if (projectRepository.existsByCodeIgnoreCase(seed.code)) return@forEach
            projectRepository.save(
                Project(
                    name = seed.name,
                    code = seed.code,
                    department = department(seed.department),
                    startDate = LocalDate.now().minusMonths(seed.startedMonthsAgo),
                )
            )
        }
    }

    private fun seedUsers(seeds: List<UserSeed>) {
        seeds.forEach { seed ->
            if (users.existsByUsernameIgnoreCase(seed.username)) return@forEach
            val userTeams = seed.teams.map { name ->
                teamRepository.findByNameIgnoreCase(name) ?: error("Seed team '$name' is missing")
            }.toMutableSet()
            users.save(
                User(
                    username = seed.username,
                    email = "${seed.username}@example.com",
                    fullName = listOf(seed.firstName, seed.lastName).joinToString(" "),
                    firstName = seed.firstName,
                    lastName = seed.lastName,
                    passwordHash = if (seed.username == ADMIN.username) adminPasswordHash else devPasswordHash,
                    role = seed.role,
                    department = seed.department?.let(::department),
                    teams = userTeams,
                )
            )
        }
    }

    private fun department(name: String): Department =
        departments.findByNameIgnoreCase(name) ?: error("Seed department '$name' is missing")

    private data class TeamSeed(
        val name: String,
        val department: String,
        val managerUsername: String,
        val projectCodes: List<String>,
    )

    private data class ProjectSeed(
        val name: String,
        val code: String,
        val department: String,
        val startedMonthsAgo: Long = 3,
    )

    private data class UserSeed(
        val username: String,
        val firstName: String,
        val lastName: String,
        val role: Role,
        val department: String?,
        val teams: List<String> = emptyList(),
    )

    private companion object {
        const val DEV_PASSWORD = "Dev@2026"
        const val ADMIN_PASSWORD = "Admin@2026"

        val ADMIN = UserSeed("admin", "Dev", "Admin", Role.ADMIN, "IT")

        val DEPARTMENTS = listOf(
            "IT" to "Software, infrastructure and data",
            "Finance" to "Budgeting, accounts and payments",
            "Operations" to "Logistics, facilities and procurement",
        )

        val LEADERSHIP = listOf(
            UserSeed("john.banda", "John", "Banda", Role.DEPARTMENT_MANAGER, "IT"),
            UserSeed("grace.phiri", "Grace", "Phiri", Role.DEPARTMENT_MANAGER, "Finance"),
            UserSeed("peter.chirwa", "Peter", "Chirwa", Role.DEPARTMENT_MANAGER, "Operations"),
            UserSeed("md.office", "Managing", "Director", Role.MANAGING_DIRECTOR, null),
        )

        val PROJECTS = listOf(
            ProjectSeed("Resource Tracker", "RT001", "IT", startedMonthsAgo = 4),
            ProjectSeed("Inventory Management", "IM001", "IT", startedMonthsAgo = 2),
            ProjectSeed("Data Platform", "DP001", "IT", startedMonthsAgo = 6),
            ProjectSeed("Annual Budget 2027", "AB027", "Finance", startedMonthsAgo = 1),
            ProjectSeed("Warehouse Upgrade", "WU001", "Operations", startedMonthsAgo = 3),
        )

        val TEAMS = listOf(
            TeamSeed("Software Development Team", "IT", "john.banda", listOf("RT001", "IM001")),
            TeamSeed("Data Engineering Team", "IT", "john.banda", listOf("DP001")),
            TeamSeed("Accounts Team", "Finance", "grace.phiri", listOf("AB027")),
            TeamSeed("Logistics Team", "Operations", "peter.chirwa", listOf("WU001")),
        )

        val TEAM_USERS = listOf(
            UserSeed("chikondi.mwale", "Chikondi", "Mwale", Role.TEAM_LEAD, "IT", listOf("Software Development Team")),
            UserSeed("tadala.kamanga", "Tadala", "Kamanga", Role.TEAM_LEAD, "IT", listOf("Data Engineering Team")),
            UserSeed("thoko.nyirenda", "Thoko", "Nyirenda", Role.TEAM_LEAD, "Finance", listOf("Accounts Team")),
            UserSeed("limbani.zulu", "Limbani", "Zulu", Role.TEAM_LEAD, "Operations", listOf("Logistics Team")),
            UserSeed("yamikani.phiri", "Yamikani", "Phiri", Role.MEMBER, "IT", listOf("Software Development Team")),
            UserSeed("mphatso.chirwa", "Mphatso", "Chirwa", Role.MEMBER, "IT", listOf("Data Engineering Team")),
        )
    }
}
