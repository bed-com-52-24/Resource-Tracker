package com.resourcetracker.user

import com.resourcetracker.auth.RefreshTokenRepository
import com.resourcetracker.department.DepartmentRepository
import com.resourcetracker.team.TeamRepository
import com.resourcetracker.common.DuplicateResourceException
import com.resourcetracker.common.BusinessRuleException
import com.resourcetracker.common.ResourceNotFoundException
import com.resourcetracker.common.WeakPasswordException
import com.resourcetracker.common.ListQuery
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val users: UserRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val encoder: PasswordEncoder,
    private val departments: DepartmentRepository,
    private val teams: TeamRepository,
) {
    companion object {
        val ASSIGNABLE_ROLES = setOf(Role.TEAM_LEAD, Role.DEPARTMENT_MANAGER, Role.ADMIN)

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

        val violations = PasswordPolicy.violations(request.password)
        if (violations.isNotEmpty()) throw WeakPasswordException(violations)

        val user = User(
            username = username,
            email = email,
            fullName = request.fullName.trim(),
            passwordHash = encoder.encode(request.password)!!,
            role = request.role,
            department = activeDepartment(request.departmentId),
            teams = activeTeams(request.teamIds ?: emptySet()),
        )

        return try {
            users.saveAndFlush(user).toResponse()
        } catch (e: DataIntegrityViolationException) {
            throw DuplicateResourceException("Username or email is already in use")
        }
    }

    @Transactional
    fun update(id: Int, request: UpdateUserRequest): UserResponse {
        val user = find(id)
        val email = request.email?.trim()

        if (email != null && !user.email.equals(email, ignoreCase = true) && users.existsByEmailIgnoreCase(email)) {
            throw DuplicateResourceException("Email '$email' is already in use")
        }

        email?.let { user.email = it }
        request.fullName?.let { user.fullName = it.trim() }
        request.departmentId?.let { user.department = activeDepartment(it) }
        request.teamIds?.let {
            user.teams.clear()
            user.teams.addAll(activeTeams(it))
        }

        request.role?.let { newRole ->
            if(newRole != user.role){
                applyRole(user, newRole)
            }
        }
        
        request.active?.let{applyActive(user, it)}

        return try {
            users.save(user).toResponse()
        } catch (e: DataIntegrityViolationException) {
            throw DuplicateResourceException("Email '${email ?: user.email}' is already in use")
        }
    }

    @Transactional(readOnly = true)
    fun list(search: String?, pageable: Pageable): Page<UserResponse> {
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
        val page = if (term == null) users.findAll(safe) else users.search(term, safe)

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
    user.role = newRole
    }
    
    private fun applyActive(user: User, active: Boolean){
        if(user.active == active) return
        
        if(!active){
            if(user.role == Role.ADMIN) ensureAnotherActiveAdmin(user)
            user.active = false
            refreshTokens.revokeAllByUserId(user.id)
        }else{
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
        id = id, username = username, email = email, fullName = fullName,
        role = role,
        departmentId = department.id,
        departmentName = department.name,
        teamIds = teams.map { it.id }.sorted(),
        teamNames = teams.map { it.name }.sorted(),
        active = active,
    )

    private fun activeDepartment(id: Int) =
        departments.findById(id).orElseThrow { ResourceNotFoundException("Department $id not found") }
            .also { if (it.status != "ACTIVE") throw BusinessRuleException("Department $id is inactive") }

    private fun activeTeams(ids: Set<Int>) =
        ids.map { teams.findById(it).orElseThrow { ResourceNotFoundException("Team $it not found") } }
            .also { found -> if (found.any { !it.isActive }) throw BusinessRuleException("Inactive teams cannot be assigned") }
            .toMutableSet()
}
