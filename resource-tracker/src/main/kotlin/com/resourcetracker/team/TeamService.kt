package com.resourcetracker.team

import com.resourcetracker.audit.AuditAction
import com.resourcetracker.audit.AuditRecord
import com.resourcetracker.audit.AuditService
import com.resourcetracker.common.BusinessRuleException
import com.resourcetracker.common.DuplicateResourceException
import com.resourcetracker.common.ResourceNotFoundException
import com.resourcetracker.department.Department
import com.resourcetracker.common.ListQuery
import com.resourcetracker.department.DepartmentRepository
import com.resourcetracker.project.Project
import com.resourcetracker.project.ProjectRepository
import com.resourcetracker.security.AuthenticatedUser
import com.resourcetracker.user.Role
import com.resourcetracker.user.User
import com.resourcetracker.user.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class TeamService(
    private val teams: TeamRepository,
    private val departments: DepartmentRepository,
    private val users: UserRepository,
    private val projects: ProjectRepository,
    private val audit: AuditService,
    private val memberHistory: TeamMemberHistoryRepository,
) {
    private val allowedSortFields = setOf("name", "isActive", "createdAt")

    @Transactional
    fun create(req: TeamRequest): TeamResponse {
        val dept = activeDepartment(req.departmentId)
        val name = req.name.trim()
        if (teams.existsByDepartmentIdAndNameIgnoreCase(dept.id, name))
            throw DuplicateResourceException("Team name already exists in this department")
        val manager = validManager(req.managerId)
        val teamProjects = activeProjects(req.projectIds)

        val saved = teams.save(
            Team(name = name, description = req.description?.trim()?.takeIf { it.isNotEmpty() }, department = dept, manager = manager, projects = teamProjects.toMutableSet())
        )
        audit.record(
            AuditRecord.currentActor(
                AuditAction.TEAM_CREATED,
                "TEAM",
                saved.id.toString(),
                newState = snapshot(saved),
                changedFields = listOf("name", "description", "departmentId", "managerId", "projectIds", "isActive"),
            ),
        )
        return saved.toResponse()
    }

    @Transactional
    fun update(id: Int, req: UpdateTeamRequest): TeamResponse {
        val team = teams.findByIdOrNull(id) ?: throw ResourceNotFoundException("Team not found")
        val before = snapshot(team)
        val dept = req.departmentId?.let(::activeDepartment) ?: team.department
        req.name?.let {
            val name = it.trim()
            if (teams.existsByDepartmentIdAndNameIgnoreCaseAndIdNot(dept.id, name, id))
                throw DuplicateResourceException("Team name already exists in this department")
            team.name = name
        }
        team.department = dept
        req.managerId?.let { team.manager = validManager(it) }
        req.projectIds?.let {
            val teamProjects = projectsForUpdate(team, it)
            team.projects.clear()
            team.projects.addAll(teamProjects)
        }
        req.isActive?.let { applyActive(team, it) }
        team.updatedAt = Instant.now()
        recordUpdate(team, before, snapshot(team))
        return team.toResponse()
    }

    @Transactional
    fun deactivate(id: Int): TeamResponse {
        val team = teams.findByIdOrNull(id) ?: throw ResourceNotFoundException("Team not found")
        val before = snapshot(team)
        applyActive(team, false)
        team.updatedAt = Instant.now()
        recordUpdate(team, before, snapshot(team))
        return team.toResponse()
    }

    @Transactional(readOnly = true)
    fun get(id: Int): TeamResponse =
        (teams.findByIdOrNull(id) ?: throw ResourceNotFoundException("Team not found")).toResponse()

    @Transactional(readOnly = true)
    fun list(
        departmentId: Int?,
        managerId: Int?,
        projectId: Int?,
        isActive: Boolean?,
        keyword: String?,
        page: Int,
        size: Int,
        sort: List<String>
    ): Page<TeamResponse> {
        if (keyword != null && keyword.length > 200)
            throw InvalidTeamRequestException("Keyword must not exceed 200 characters")
        if (page < 0 || size < 1 || size > 100)
            throw InvalidTeamRequestException("page must be >= 0 and size must be between 1 and 100")

        val spec = TeamSpecifications.filter(departmentId, managerId, projectId, isActive, keyword)
        val pageable = PageRequest.of(page, size, try {
            ListQuery.sort(sort, allowedSortFields, "name")
        } catch (e: IllegalArgumentException) {
            throw InvalidTeamRequestException(e.message ?: "Invalid sort")
        })
        return teams.findAll(spec, pageable).map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun members(teamId: Int, role: Role?, active: Boolean?, search: String?, pageable: Pageable): Page<User> {
        val team = teams.findByIdOrNull(teamId) ?: throw ResourceNotFoundException("Team not found")
        if (!team.isActive) throw BusinessRuleException("Team is inactive")

        val spec = TeamMemberSpecifications.filter(teamId, role, active, search)
        return users.findAll(spec, pageable)
    }

    @Transactional
    fun addMembers(teamId: Int, usernames: Set<String>): List<User> {
        val team = teams.findByIdOrNull(teamId) ?: throw ResourceNotFoundException("Team not found")
        if (!team.isActive) throw BusinessRuleException("Team is inactive")
        val members = usernames.map { username -> users.findByUsername(username) ?: throw ResourceNotFoundException("User not found: $username") }

        val actor = currentActor()
        members.forEach { user ->
            if (!user.active) throw BusinessRuleException("User is inactive")
            if (user.teams.any { it.id == teamId }) throw BusinessRuleException("User is already a member of this team")
            user.teams.add(team)
            memberHistory.save(TeamMemberHistory(user = user, fromTeam = null, toTeam = team, action = TeamMemberAction.ADDED, performedBy = actor))
        }
        return members
    }

    @Transactional
    fun transfer(teamId: Int, username: String, toTeamId: Int): User {
        if (teamId == toTeamId) throw BusinessRuleException("Transfer target must be different from the source team")
        val source = teams.findByIdOrNull(teamId) ?: throw ResourceNotFoundException("Team not found")
        val target = teams.findByIdOrNull(toTeamId) ?: throw ResourceNotFoundException("Team not found")
        if (!source.isActive || !target.isActive) throw BusinessRuleException("Teams must be active")
        val user = users.findByUsername(username) ?: throw ResourceNotFoundException("User not found")
        if (!user.active) throw BusinessRuleException("User is inactive")
        if (!user.teams.any { it.id == teamId }) throw BusinessRuleException("User is not a member of the source team")
        if (user.teams.any { it.id == toTeamId }) throw BusinessRuleException("User is already a member of the target team")

        val actor = currentActor()
        user.teams.removeIf { it.id == teamId }
        user.teams.add(target)
        memberHistory.save(TeamMemberHistory(user = user, fromTeam = source, toTeam = target, action = TeamMemberAction.TRANSFERRED, performedBy = actor))
        return user
    }

    @Transactional
    fun removeMember(teamId: Int, username: String): User {
        val team = teams.findByIdOrNull(teamId) ?: throw ResourceNotFoundException("Team not found")
        if (!team.isActive) throw BusinessRuleException("Team is inactive")
        val user = users.findByUsername(username) ?: throw ResourceNotFoundException("User not found")
        if (!user.active) throw BusinessRuleException("User is inactive")
        if (!user.teams.any { it.id == teamId }) throw BusinessRuleException("User is not a member of the team")
        if (team.manager?.id == user.id) throw BusinessRuleException("Reassign the team manager before removing them")
        if (user.role == Role.MEMBER && user.teams.size <= 1) throw BusinessRuleException("MEMBER users must retain at least one team")

        user.teams.removeIf { it.id == teamId }
        val actor = currentActor()
        memberHistory.save(TeamMemberHistory(user = user, fromTeam = team, toTeam = null, action = TeamMemberAction.REMOVED, performedBy = actor))
        return user
    }

    @Transactional(readOnly = true)
    fun history(teamId: Int): List<TeamMemberHistoryResponse> {
        val team = teams.findByIdOrNull(teamId) ?: throw ResourceNotFoundException("Team not found")
        if (!team.isActive) throw BusinessRuleException("Team is inactive")
        return memberHistory.findHistoryForTeam(teamId).map { history ->
            TeamMemberHistoryResponse(
                id = history.id,
                user = com.resourcetracker.common.UserReference(history.user.id, history.user.fullName ?: history.user.username),
                fromTeam = history.fromTeam?.let { com.resourcetracker.common.TeamReference(it.id, it.name) },
                toTeam = history.toTeam?.let { com.resourcetracker.common.TeamReference(it.id, it.name) },
                action = history.action,
                performedBy = com.resourcetracker.common.UserReference(history.performedBy.id, history.performedBy.fullName ?: history.performedBy.username),
                createdAt = history.createdAt,
            )
        }
    }

    private fun currentActor(): User {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedUser
            ?: throw BusinessRuleException("Authentication required")
        return users.findByIdOrNull(principal.id) ?: throw ResourceNotFoundException("Actor not found")
    }

    private fun applyActive(team: Team, active: Boolean) {
        team.isActive = active
    }

    private fun snapshot(team: Team) = mapOf(
        "name" to team.name,
        "departmentId" to team.department.id,
        "managerId" to team.manager?.id,
        "projectIds" to team.projects.map { it.id }.sorted(),
        "isActive" to team.isActive,
    )

    private fun recordUpdate(team: Team, before: Map<String, Any?>, after: Map<String, Any?>) {
        val changed = before.keys.filter { before[it] != after[it] }
        if (changed.isEmpty()) return
        val action = when {
            "isActive" in changed && team.isActive -> AuditAction.TEAM_RESTORED
            "isActive" in changed -> AuditAction.TEAM_ARCHIVED
            else -> AuditAction.TEAM_UPDATED
        }
        audit.record(
            AuditRecord.currentActor(
                action,
                "TEAM",
                team.id.toString(),
                previousState = before,
                newState = after,
                changedFields = changed,
            ),
        )
    }

    // Only newly added projects must be active, so a team can still be edited or deactivated after one of its existing projects was deactivated.
    private fun projectsForUpdate(team: Team, ids: Set<Int>): List<Project> {
        if (ids.isEmpty()) return emptyList()
        val found = projects.findAllById(ids)
        if (found.size != ids.size) throw ResourceNotFoundException("One or more projects not found")
        val existing = team.projects.map { it.id }.toSet()
        if (found.any { it.id !in existing && !it.active })
            throw InvalidTeamRequestException("Inactive projects cannot be assigned to a team")
        return found
    }

    private fun activeDepartment(id: Int): Department =
        departments.findByIdOrNull(id)?.takeIf { it.status.equals("ACTIVE", ignoreCase = true) }
            ?: throw ResourceNotFoundException("Department not found or inactive")

    private fun validManager(id: Int): User {
        val manager = users.findByIdOrNull(id) ?: throw ResourceNotFoundException("Manager not found")
        if (!manager.active || manager.role != Role.DEPARTMENT_MANAGER)
            throw InvalidTeamRequestException("Manager must be an active user with the Department Manager role")
        return manager
    }

    private fun activeProjects(ids: Set<Int>): List<Project> {
        if (ids.isEmpty()) return emptyList()
        val found = projects.findAllById(ids)
        if (found.size != ids.size) throw ResourceNotFoundException("One or more projects not found")
        if (found.any { !it.active }) throw InvalidTeamRequestException("Inactive projects cannot be assigned to a team")
        return found
    }

}
