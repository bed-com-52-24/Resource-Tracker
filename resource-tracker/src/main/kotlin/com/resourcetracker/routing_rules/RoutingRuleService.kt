package com.resourcetracker.routing_rules

import com.resourcetracker.common.ConflictException
import com.resourcetracker.common.NotFoundException
import com.resourcetracker.common.ValidationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime

@Service
class RoutingRuleService(
    private val rules: RoutingRuleRepository,
    private val audits: AuditEntryRepository,
    private val validator: RoutingReferenceValidator,
    private val objectMapper: ObjectMapper
) {

    @Transactional(readOnly = true)
    fun list(): List<RoutingRule> = rules.findAllNotDeleted()

    @Transactional(readOnly = true)
    fun get(id: Int): RoutingRule =
        rules.findNotDeletedById(id)
            .orElseThrow { NotFoundException("Routing rule not found") }

    @Transactional
    fun create(request: RoutingRuleRequest, actor: String): RoutingRule {
        validate(request)

        val rule = RoutingRule(
            departmentId = request.departmentId,
            projectId = request.projectId,
            teamId = request.teamId,
            managerId = request.managerId!!,
            priority = request.priority!!,
            active = false,
            createdBy = actor,
            updatedBy = actor
        )

        val saved = rules.saveAndFlush(rule)
        audit(saved, "CREATE", actor, null, snapshot(saved))
        return saved
    }

    @Transactional
    fun update(
        id: Int,
        request: UpdateRoutingRuleRequest,
        actor: String
    ): RoutingRule {
        val rule = get(id)
        validate(request)

        if (request.active == false) applyActive(rule, false, actor)

        val before = snapshot(rule)

        rule.departmentId = request.departmentId
        rule.projectId = request.projectId
        rule.teamId = request.teamId
        rule.managerId = request.managerId!!
        rule.priority = request.priority!!
        rule.updatedBy = actor

        val saved = try {
            val saved = rules.saveAndFlush(rule)
            audit(saved, "UPDATE", actor, before, snapshot(saved))
            saved
        } catch (ex: DataIntegrityViolationException) {
            throw ConflictException(
                "An active rule already has this department/project/team"
            )
        }
        if (request.active == true) applyActive(saved, true, actor)
        return saved
    }

    private fun applyActive(rule: RoutingRule, active: Boolean, actor: String): RoutingRule {
        if (!active) {
            if (!rule.active) return rule
            val before = snapshot(rule)
            rule.active = false
            rule.updatedBy = actor
            val saved = rules.saveAndFlush(rule)
            audit(saved, "DEACTIVATE", actor, before, snapshot(saved))
            return saved
        }

        validator.requireActiveDepartment(rule.departmentId ?: 0)
        rule.projectId?.let { validator.requireActiveProject(it) }
        rule.teamId?.let { validator.requireActiveTeam(it) }
        validator.requireActiveDepartmentManager(rule.managerId)

        if (rule.active) return rule

        if (rules.existsActiveCombination(
                rule.departmentId,
                rule.projectId,
                rule.teamId,
                rule.id
            )
        ) {
            throw ConflictException(
                "An active rule already has this department/project/team"
            )
        }

        val before = snapshot(rule)
        rule.active = true
        rule.updatedBy = actor

        return try {
            val saved = rules.saveAndFlush(rule)
            audit(saved, "ACTIVATE", actor, before, snapshot(saved))
            saved
        } catch (ex: DataIntegrityViolationException) {
            throw ConflictException(
                "An active rule already has this department/project/team"
            )
        }
    }

    private fun validate(request: RoutingRuleRequest) {
        validateReferences(
            request.departmentId ?: throw ValidationException("Department is required"),
            request.projectId,
            request.teamId,
            request.managerId ?: throw ValidationException("Manager is required"),
            request.priority ?: throw ValidationException("Priority is required")
        )
    }

    private fun validate(request: UpdateRoutingRuleRequest) {
        val departmentId = request.departmentId
            ?: throw ValidationException("Department is required")
        val managerId = request.managerId
            ?: throw ValidationException("Manager is required")
        val priority = request.priority
            ?: throw ValidationException("Priority is required")

        if (request.active != false) {
            validateReferences(departmentId, request.projectId, request.teamId, managerId, priority)
        }
    }

    private fun validateReferences(
        departmentId: Int,
        projectId: Int?,
        teamId: Int?,
        managerId: Int,
        priority: Int,
    ) {
        if (priority < 0) throw ValidationException("Priority must be >= 0")
        validator.requireActiveDepartment(departmentId)
        projectId?.let { validator.requireActiveProject(it) }
        teamId?.let { validator.requireActiveTeam(it) }
        validator.requireActiveDepartmentManager(managerId)
    }

    private fun snapshot(rule: RoutingRule): String =
        objectMapper.writeValueAsString(
            mapOf(
                "departmentId" to rule.departmentId,
                "projectId" to rule.projectId,
                "teamId" to rule.teamId,
                "managerId" to rule.managerId,
                "priority" to rule.priority,
                "active" to rule.active,
                "deletedAt" to rule.deletedAt
            )
        )

    private fun audit(
        rule: RoutingRule,
        action: String,
        actor: String,
        before: String?,
        after: String?
    ) {
        audits.save(
            AuditEntry(
                entityId = requireNotNull(rule.id),
                action = action,
                actor = actor,
                oldValues = before,
                newValues = after
            )
        )
    }
}
