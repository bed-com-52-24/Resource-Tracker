package com.resourcetracker.user

import com.resourcetracker.auth.RefreshTokenRepository
import com.resourcetracker.common.DuplicateResourceException
import com.resourcetracker.common.BusinessRuleException
import com.resourcetracker.common.ResourceNotFoundException
import com.resourcetracker.common.WeakPasswordException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val users: UserRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val encoder: PasswordEncoder,
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
            department = cleanDepartment(request.department) ?: "",
            teams = cleanTeams(request.teams),
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
        val email = request.email.trim()

        if (!user.email.equals(email, ignoreCase = true) && users.existsByEmailIgnoreCase(email)) {
            throw DuplicateResourceException("Email '$email' is already in use")
        }

        user.email = email
        user.fullName = request.fullName.trim()
        user.department = cleanDepartment(request.department) ?: ""
        user.teams.clear()
        user.teams.addAll(cleanTeams(request.teams))

        return try {
            users.saveAndFlush(user).toResponse()
        } catch (e: DataIntegrityViolationException) {
            throw DuplicateResourceException("Email '$email' is already in use")
        }
    }

    @Transactional
    fun changeRole(id: Int, newRole: Role): UserResponse {
        requireAssignable(newRole)
        val user = find(id)

        if (user.active && user.role == Role.ADMIN && newRole != Role.ADMIN) {
            ensureAnotherActiveAdmin(user)
        }
        user.role = newRole
        return users.save(user).toResponse()
    }

    @Transactional
    fun deactivate(id: Int): UserResponse {
        val user = find(id)
        if (!user.active) return user.toResponse()

        if (user.role == Role.ADMIN) ensureAnotherActiveAdmin(user)

        user.active = false
        users.save(user)

        refreshTokens.revokeAllByUserId(user.id)
        return user.toResponse()
    }

    @Transactional(readOnly = true)
    fun list(search: String?, pageable: Pageable): Page<UserResponse> {
        val safe = PageRequest.of(
            pageable.pageNumber.coerceAtLeast(0),
            pageable.pageSize.coerceIn(1, MAX_PAGE_SIZE),
            pageable.sort.filter { it.property in SORTABLE }
                .toList()
                .let { if (it.isNotEmpty()) Sort.by(it) else Sort.by("username") },
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

    private fun ensureAnotherActiveAdmin(target: User) {
        val admins = users.findActiveByRoleForUpdate(Role.ADMIN)
        if (admins.none { it.id != target.id }) {
            throw BusinessRuleException("Cannot demote or deactivate the last active Admin")
        }
    }

    private fun cleanDepartment(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() }?.also {
            if (it.length > 100) throw BusinessRuleException("Department must be at most 100 characters")
        }

    private fun cleanTeams(values: Set<String>): MutableSet<String> =
        values.map { it.trim() }.filter { it.isNotEmpty() }
            .onEach { if (it.length > 100) throw BusinessRuleException("Team names must be at most 100 characters") }
            .toMutableSet()

    private fun User.toResponse() = UserResponse(
        id = id, username = username, email = email, fullName = fullName,
        role = role, department = department, teams = teams.sorted(), active = active,
    )
}
