package com.resourcetracker.dev

import com.resourcetracker.department.Department
import com.resourcetracker.department.DepartmentRepository
import com.resourcetracker.project.CreateProjectRequest
import com.resourcetracker.project.ProjectRepository
import com.resourcetracker.project.ProjectService
import com.resourcetracker.resource.CreateResourceRequest
import com.resourcetracker.resource.ResourceCategory
import com.resourcetracker.resource.ResourceRepository
import com.resourcetracker.resource.ResourceService
import com.resourcetracker.resource.ResourceType
import com.resourcetracker.team.TeamRepository
import com.resourcetracker.team.TeamRequest
import com.resourcetracker.team.TeamService
import com.resourcetracker.user.Role
import com.resourcetracker.user.User
import com.resourcetracker.user.UserRepository
import com.resourcetracker.vendor.Vendor
import com.resourcetracker.vendor.VendorRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * Seeds sample data for local development.
 *
 * Safe to run on every startup: each item is skipped if it already exists.
 * Projects, teams and resources go through their services so the real business rules apply.
 */
@Component
@Profile("dev")
class DevDataSeeder(
    private val users: UserRepository,
    private val departments: DepartmentRepository,
    private val encoder: PasswordEncoder,
    private val vendors: VendorRepository,
    private val projects: ProjectService,
    private val teams: TeamService,
    private val resources: ResourceService,
    private val projectRepository: ProjectRepository,
    private val teamRepository: TeamRepository,
    private val resourceRepository: ResourceRepository,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(DevDataSeeder::class.java)
    private val devPasswordHash by lazy { encoder.encode(DEV_PASSWORD)!! }

    override fun run(args: ApplicationArguments) {
        seedUser()
        //seedDepartments()
        //seedVendors()
        //seedProjects()
        //seedManagers()   // teams need an active DEPARTMENT_MANAGER, so these come first
        //seedTeams()
        //seedTeamUsers()  // MEMBER and TEAM_LEAD users need at least one team, so these come last
        //seedResources()
        log.info("Dev seed data ready. Log in as 'admin' / 'Admin@2026', or any other seeded user / '$DEV_PASSWORD'.")
    }

    private fun seedUser() {
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

    private fun seedVendors() {
        VENDORS.forEach { (name, contact) ->
            if (!vendors.existsByNameIgnoreCase(name)) {
                vendors.save(Vendor(name = name, contactInformation = contact))
            }
        }
    }

    private fun seedProjects() {
        PROJECTS.forEach { p ->
            if (projectRepository.existsByCodeIgnoreCase(p.code)) return@forEach
            projects.create(
                CreateProjectRequest(
                    name = p.name,
                    code = p.code,
                    departmentId = department(p.department).id,
                    startDate = LocalDate.now().minusMonths(p.startedMonthsAgo),
                )
            )
        }
    }

    private fun seedManagers() {
        LEADERSHIP.forEach(::createUser)
    }

    private fun seedTeams() {
        val projectIdsByCode = projectRepository.findAll().associate { it.code to it.id }
        TEAMS.forEach { t ->
            val dept = department(t.department)
            if (teamRepository.existsByDepartmentIdAndNameIgnoreCase(dept.id, t.name)) return@forEach
            val manager = users.findByUsername(t.managerUsername)
                ?: error("Seed manager '${t.managerUsername}' is missing")
            teams.create(
                TeamRequest(
                    name = t.name,
                    departmentId = dept.id,
                    managerId = manager.id,
                    projectIds = t.projectCodes.map { projectIdsByCode.getValue(it) }.toSet(),
                )
            )
        }
    }

    private fun seedTeamUsers() {
        TEAM_USERS.forEach(::createUser)
    }

    private fun seedResources() {
        val actorId = users.findByUsername("admin")?.id ?: error("Seed admin is missing")
        val existingNames = resourceRepository.findAll().map { it.name.lowercase() }.toSet()
        val vendorIds = vendors.findAll().associate { it.name to it.id }

        RESOURCES.filter { it.name.lowercase() !in existingNames }.forEach { r ->
            resources.create(
                CreateResourceRequest(
                    name = r.name,
                    type = r.type,
                    category = r.category,
                    unitOfMeasure = r.unit,
                    description = r.description,
                    quantity = r.quantity,
                    location = r.location,
                    reorderLevel = r.reorderLevel,
                    preferredVendorId = r.vendor?.let { vendorIds.getValue(it) },
                ),
                actorId,
            )
        }
    }

    private fun createUser(seed: UserSeed) {
        if (users.existsByUsernameIgnoreCase(seed.username)) return
        val userTeams = seed.teams.map { name ->
            teamRepository.findByNameIgnoreCase(name) ?: error("Seed team '$name' is missing")
        }.toMutableSet()
        users.save(
            User(
                username = seed.username,
                email = "${seed.username}@example.com",
                fullName = seed.fullName,
                passwordHash = devPasswordHash,
                role = seed.role,
                department = department(seed.department),
                teams = userTeams,
            )
        )
    }

    private fun department(name: String): Department =
        departments.findByNameIgnoreCase(name) ?: error("Seed department '$name' is missing")

    private data class ProjectSeed(
        val name: String,
        val code: String,
        val department: String,
        val startedMonthsAgo: Long = 3,
    )

    private data class TeamSeed(
        val name: String,
        val department: String,
        val managerUsername: String,
        val projectCodes: List<String> = emptyList(),
    )

    private data class UserSeed(
        val username: String,
        val fullName: String,
        val role: Role,
        val department: String,
        val teams: List<String> = emptyList(),
    )

    private data class ResourceSeed(
        val name: String,
        val type: ResourceType,
        val category: ResourceCategory,
        val unit: String,
        val description: String? = null,
        val quantity: Int? = null,        // INTERNAL only
        val location: String? = null,
        val reorderLevel: Int = 0,
        val department: String? = null,   // INTERNAL only
        val vendor: String? = null,       // EXTERNAL only
    )

    private companion object {
        const val DEV_PASSWORD = "Dev@2026"

        val DEPARTMENTS = listOf(
            "IT" to "Software, infrastructure and data",
            "Finance" to "Budgeting, accounts and payments",
            "Operations" to "Logistics, facilities and procurement",
        )

        val VENDORS = listOf(
            "Tech Supplies Malawi" to "sales@techsupplies.example.com | +265 999 000 101",
            "Lilongwe Office Mart" to "orders@officemart.example.com | +265 999 000 102",
            "Cloudline Hosting" to "support@cloudline.example.com",
            "SoftLicence Africa" to "licensing@softlicence.example.com",
        )

        val PROJECTS = listOf(
            ProjectSeed("Resource Tracker", "RT001", "IT", startedMonthsAgo = 4),
            ProjectSeed("Inventory Management", "IM001", "IT", startedMonthsAgo = 2),
            ProjectSeed("Data Platform", "DP001", "IT", startedMonthsAgo = 6),
            ProjectSeed("Annual Budget 2027", "AB027", "Finance", startedMonthsAgo = 1),
            ProjectSeed("Warehouse Upgrade", "WU001", "Operations", startedMonthsAgo = 3),
        )


        val LEADERSHIP = listOf(
            UserSeed("john.banda", "John Banda", Role.DEPARTMENT_MANAGER, "IT"),
            UserSeed("grace.phiri", "Grace Phiri", Role.DEPARTMENT_MANAGER, "Finance"),
            UserSeed("peter.chirwa", "Peter Chirwa", Role.DEPARTMENT_MANAGER, "Operations"),
            UserSeed("md.office", "Managing Director", Role.MANAGING_DIRECTOR, "Operations"),
        )

        val TEAMS = listOf(
            TeamSeed("Software Development Team", "IT", "john.banda", listOf("RT001", "IM001")),
            TeamSeed("Data Engineering Team", "IT", "john.banda", listOf("DP001")),
            TeamSeed("Accounts Team", "Finance", "grace.phiri", listOf("AB027")),
            TeamSeed("Logistics Team", "Operations", "peter.chirwa", listOf("WU001")),
        )

        val TEAM_USERS = listOf(
            UserSeed("chikondi.mwale", "Chikondi Mwale", Role.TEAM_LEAD, "IT", listOf("Software Development Team")),
            UserSeed("tadala.kamanga", "Tadala Kamanga", Role.TEAM_LEAD, "IT", listOf("Data Engineering Team")),
            UserSeed("thoko.nyirenda", "Thoko Nyirenda", Role.TEAM_LEAD, "Finance", listOf("Accounts Team")),
            UserSeed("limbani.zulu", "Limbani Zulu", Role.TEAM_LEAD, "Operations", listOf("Logistics Team")),
            UserSeed("yamikani.phiri", "Yamikani Phiri", Role.MEMBER, "IT", listOf("Software Development Team")),
            UserSeed("mphatso.chirwa", "Mphatso Chirwa", Role.MEMBER, "IT", listOf("Data Engineering Team")),
        )

        val RESOURCES = listOf(
            // INTERNAL: department and quantity required, no vendor
            ResourceSeed(
                "Laptop", ResourceType.INTERNAL, ResourceCategory.EQUIPMENT, "unit",
                description = "Standard developer laptop", quantity = 15,
                location = "IT Store", reorderLevel = 3, department = "IT",
            ),
            ResourceSeed(
                "Monitor 24 inch", ResourceType.INTERNAL, ResourceCategory.EQUIPMENT, "unit",
                quantity = 20, location = "IT Store", reorderLevel = 5, department = "IT",
            ),
            ResourceSeed(
                "A4 Printing Paper", ResourceType.INTERNAL, ResourceCategory.CONSUMABLE, "ream",
                quantity = 200, location = "Main Store", reorderLevel = 50, department = "Operations",
            ),
            ResourceSeed(
                "Printer Toner Cartridge", ResourceType.INTERNAL, ResourceCategory.CONSUMABLE, "cartridge",
                quantity = 12, location = "Main Store", reorderLevel = 4, department = "Operations",
            ),
            ResourceSeed(
                "Office Chair", ResourceType.INTERNAL, ResourceCategory.EQUIPMENT, "unit",
                quantity = 8, location = "Main Store", reorderLevel = 2, department = "Operations",
            ),
            // Stock below its reorder level, handy for testing low-stock behaviour
            ResourceSeed(
                "Whiteboard Markers", ResourceType.INTERNAL, ResourceCategory.CONSUMABLE, "box",
                quantity = 2, location = "Main Store", reorderLevel = 10, department = "Operations",
            ),
            // Out of stock, handy for testing the OUT_OF_STOCK status
            ResourceSeed(
                "USB-C Docking Station", ResourceType.INTERNAL, ResourceCategory.EQUIPMENT, "unit",
                quantity = 0, location = "IT Store", reorderLevel = 2, department = "IT",
            ),
            // EXTERNAL: no department or quantity, vendor optional
            ResourceSeed(
                "Cloud Hosting Subscription", ResourceType.EXTERNAL, ResourceCategory.SOFTWARE, "month",
                vendor = "Cloudline Hosting",
            ),
            ResourceSeed(
                "IDE Licence", ResourceType.EXTERNAL, ResourceCategory.SOFTWARE, "license",
                vendor = "SoftLicence Africa",
            ),
            ResourceSeed(
                "Conference Room Hire", ResourceType.EXTERNAL, ResourceCategory.OTHER, "day",
            ),
        )
    }
}
