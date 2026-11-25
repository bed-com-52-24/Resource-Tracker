package com.resourcetracker.user

import com.resourcetracker.common.DepartmentReference
import com.resourcetracker.common.TeamReference
import com.resourcetracker.auth.RefreshTokenRepository
import com.resourcetracker.auth.PasswordTokenPurpose
import com.resourcetracker.auth.PasswordTokenRepository
import com.resourcetracker.auth.PasswordTokenService
import com.resourcetracker.department.Department
import com.resourcetracker.department.DepartmentRepository
import com.resourcetracker.team.TeamRepository
import com.resourcetracker.common.DuplicateResourceException
import com.resourcetracker.common.BusinessRuleException
import com.resourcetracker.common.ResourceNotFoundException
import com.resourcetracker.common.ListQuery
import com.resourcetracker.security.AuthenticatedUser
import com.resourcetracker.audit.AuditAction
import com.resourcetracker.audit.AuditRecord
import com.resourcetracker.audit.AuditService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
@Service
class UserService(
    private val users: UserRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val departments: DepartmentRepository,
    private val teams: TeamRepository,
    private val audit: AuditService,
    private val passwordTokens: PasswordTokenRepository,
    private val passwordTokenService: PasswordTokenService,
) {
    companion object {
        val ASSIGNABLE_ROLES = setOf(Role.MEMBER, Role.TEAM_LEAD, Role.DEPARTMENT_MANAGER, Role.ADMIN, Role.MANAGING_DIRECTOR)

        private val SORTABLE = setOf("username", "email", "fullName", "role", "department", "active")
        private const val MAX_PAGE_SIZE = 100
    }

    @Transactional
    fun create(request: CreateUserRequest): UserResponse {
        requireAssignable(request.role)

        val username = request.username.trim()
        val email = request.email.trim()

        if (users.existsByUsernameIgnoreCase(username)) {
            throw DuplicateResourceException("Username '$username' is already taken")
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw DuplicateResourceException("Email '$email' is already in use")
        }

        if (request.role in setOf(Role.MEMBER, Role.TEAM_LEAD) && request.teamIds.isNullOrEmpty()) {
            throw BusinessRuleException("${request.role} users must be assigned to at least one team")
        }
        if (request.role in setOf(Role.MEMBER, Role.TEAM_LEAD) && request.departmentId == null) {
            throw BusinessRuleException("${request.role} users require a department")
        }
        if (request.role in setOf(Role.ADMIN, Role.MANAGING_DIRECTOR) && request.teamIds?.isNotEmpty() == true) {
            throw BusinessRuleException("Admin or Managing directors are not assigned to teams")
        }

        val department = if (request.role == Role.MANAGING_DIRECTOR) null else request.departmentId?.let(::activeDepartment)
        val userTeams = activeTeams(request.teamIds ?: emptySet(), department?.id)
        val user = User(
            username = username,
            email = email,
            fullName = listOfNotNull(request.firstName.trim(), request.middleName?.trim()?.takeIf(String::isNotEmpty), request.lastName.trim()).joinToString(" "),
            firstName = request.firstName.trim(),
            lastName = request.lastName.trim(),
            middleName = request.middleName?.trim()?.takeIf(String::isNotEmpty),
            position = request.position?.trim()?.takeIf(String::isNotEmpty),
            phone = request.phone?.trim()?.takeIf(String::isNotEmpty),
            address = request.address?.trim()?.takeIf(String::isNotEmpty),
            title = request.title?.trim()?.takeIf(String::isNotEmpty),
            passwordHash = null,
            role = request.role,
            department = department,
            teams = userTeams,
        )
        val saved = try {
            users.saveAndFlush(user)
        } catch (e: DataIntegrityViolationException) {
            throw DuplicateResourceException("Username or email is already in use")
        }
        passwordTokenService.issue(saved, PasswordTokenPurpose.SETUP, expiresAt = null)
        audit.record(
            AuditRecord.currentActor(
                AuditAction.USER_CREATED,
                "USER",
                saved.id.toString(),
                newState = snapshot(saved),
                changedFields = listOf("username", "email", "fullName", "role", "departmentId", "teamIds", "active"),
            ),
        )
        return saved.toResponse()
    }

    @Transactional
    fun update(id: Int, request: UpdateUserRequest): UserResponse {
        val user = find(id)
        val before = snapshot(user)

        request.firstName?.let { user.firstName = it.trim() }
        request.lastName?.let { user.lastName = it.trim() }
        request.middleName?.let { user.middleName = it.trim().takeIf(String::isNotEmpty) }
        request.position?.let { user.position = it.trim().takeIf(String::isNotEmpty) }
        request.phone?.let { user.phone = it.trim().takeIf(String::isNotEmpty) }
        request.address?.let { user.address = it.trim().takeIf(String::isNotEmpty) }
        request.title?.let { user.title = it.trim().takeIf(String::isNotEmpty) }
        if (request.firstName != null || request.lastName != null || request.middleName != null) {
            user.fullName = listOfNotNull(user.firstName, user.middleName, user.lastName).joinToString(" ")
        }

        request.role?.let { newRole ->
            if(newRole != user.role){
                applyRole(user, newRole)
            }
        }

        request.active?.let { applyActive(user, it) }
        val saved = users.saveAndFlush(user)
        val after = snapshot(saved)
        val changed = changedFields(before, after)
        if (changed.isNotEmpty()) {
            val action = when {
                "role" in changed -> AuditAction.USER_ROLE_UPDATED
                "active" in changed && saved.active -> AuditAction.USER_RESTORED
                "active" in changed -> AuditAction.USER_ARCHIVED
                else -> AuditAction.USER_UPDATED
            }
            audit.record(
                AuditRecord.currentActor(
                    action,
                    "USER",
                    saved.id.toString(),
                    previousState = before,
                    newState = after,
                    changedFields = changed,
                ),
            )
        }
        return saved.toResponse()
    }

    @Transactional
    fun resendSetup(id: Int): UserResponse {
        val user = find(id)
        if (!user.active) {
            throw BusinessRuleException("Cannot send password setup for an inactive user")
        }
        if (user.passwordHash != null) {
            throw BusinessRuleException("Password setup is already complete")
        }
        passwordTokens.deleteByUserIdAndPurposeAndUsedAtIsNull(user.id, PasswordTokenPurpose.SETUP)
        passwordTokenService.issue(user, PasswordTokenPurpose.SETUP, expiresAt = null)
        return user.toResponse()
    }

    @Transactional(readOnly = true)
    fun list(search: String?, pageable: Pageable, caller: AuthenticatedUser): Page<UserResponse> {
        val requestedSort = pageable.sort.firstOrNull()
        val safe = ListQuery.pageable(
            pageable.pageNumber,
            pageable.pageSize,
            requestedSort?.property,
            requestedSort?.direction?.name,
            SORTABLE,
            "username",
            MAX_PAGE_SIZE,
        )

        val term = search?.trim()?.takeIf { it.isNotEmpty() }
        val page = if (caller.role == Role.DEPARTMENT_MANAGER) {
            val departmentId = users.findById(caller.id).orElseThrow { ResourceNotFoundException("User not found") }
                .department?.id ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Department access is not configured")
            val pattern = term?.let { "%${it.lowercase()}%" }
            val departmentScope = Specification<User> { root, _, criteria ->
                val inDepartment = criteria.equal(root.get<Department>("department").get<Int>("id"), departmentId)
                if (pattern == null) {
                    inDepartment
                } else {
                    criteria.and(
                        inDepartment,
                        criteria.or(
                            criteria.like(criteria.lower(root.get<String>("username")), pattern),
                            criteria.like(criteria.lower(root.get<String>("email")), pattern),
                            criteria.like(criteria.lower(criteria.coalesce(root.get("fullName"), root.get("username"))), pattern),
                        ),
                    )
                }
            }
            users.findAll(departmentScope, safe)
        } else if (term == null) {
            users.findAll(safe)
        } else {
            users.search(term, safe)
        }

        return page.map { it.toResponse() }
    }

    private fun find(id: Int): User =
        users.findById(id).orElseThrow { ResourceNotFoundException("User $id not found") }

    private fun requireAssignable(role: Role) {
        if (role !in ASSIGNABLE_ROLES) {
            throw BusinessRuleException("Role must be one of: ${ASSIGNABLE_ROLES.joinToString()}")
        }
    }

    private fun applyRole(user: User, newRole: Role) {
        requireAssignable(newRole)
        if (user.active && user.role == Role.ADMIN && newRole != Role.ADMIN) {
            ensureAnotherActiveAdmin(user)
        }
        if (newRole == Role.MANAGING_DIRECTOR) {
            user.department = null
            user.teams.clear()
        }
        user.role = newRole
    }

    private fun applyActive(user: User, active: Boolean){
        if (user.active == active) return

        if (!active) {
            if (user.role == Role.ADMIN) ensureAnotherActiveAdmin(user)
            user.active = false
            refreshTokens.revokeAllByUserId(user.id)
            passwordTokens.deleteByUserIdAndPurposeAndUsedAtIsNull(user.id, PasswordTokenPurpose.SETUP)
        } else {
            user.active = true
        }
    }

    private fun ensureAnotherActiveAdmin(target: User) {
        val admins = users.findActiveByRoleForUpdate(Role.ADMIN)
        if (admins.none { it.id != target.id }) {
            throw BusinessRuleException("Cannot demote or deactivate the last active Admin")
        }
    }

    private fun User.toResponse() = UserResponse(
        id = id, username = username, email = email, fullName = fullName ?: username,
        firstName = firstName, lastName = lastName, middleName = middleName,
        position = position, phone = phone, address = address, title = title,
        role = role,
        department = department?.let { DepartmentReference(it.id, it.name) },
        teams = teams.map { TeamReference(it.id, it.name) }.sortedBy { it.name },
        active = active,
        passwordSet = passwordHash != null,
    )

    private fun snapshot(user: User) = mapOf(
        "username" to user.username,
        "email" to user.email,
        "fullName" to user.fullName,
        "firstName" to user.firstName, "lastName" to user.lastName, "middleName" to user.middleName,
        "position" to user.position, "phone" to user.phone, "address" to user.address, "title" to user.title,
        "role" to user.role.name,
        "departmentId" to user.department?.id,
        "teamIds" to user.teams.map { it.id }.sorted(),
        "active" to user.active,
    )

    private fun changedFields(before: Map<String, Any?>, after: Map<String, Any?>): List<String> =
        before.keys.filter { before[it] != after[it] }

    private fun activeDepartment(id: Int) =
        departments.findById(id).orElseThrow { ResourceNotFoundException("Department $id not found") }
            .also { if (it.status != "ACTIVE") throw BusinessRuleException("Department $id is inactive") }

    private fun activeTeams(ids: Set<Int>, departmentId: Int?) =
        ids.map { teams.findById(it).orElseThrow { ResourceNotFoundException("Team $it not found") } }
            .also { found ->
                if (found.any { !it.isActive }) throw BusinessRuleException("Inactive teams cannot be assigned")
                if (found.any { it.department.id != departmentId }) {
                    throw BusinessRuleException("Teams must belong to the user's department")
                }
            }
            .toMutableSet()
}
