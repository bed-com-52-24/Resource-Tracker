package com.resourcetracker.routing_rules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import tools.jackson.databind.ObjectMapper
import java.util.Optional

class RoutingRuleServiceTest {
    private val rules = mock(RoutingRuleRepository::class.java)
    private val audits = mock(AuditEntryRepository::class.java)
    private val validator = mock(RoutingReferenceValidator::class.java)
    private val mapper = mock(ObjectMapper::class.java)
    private val service = RoutingRuleService(rules, audits, validator, mapper)

    private fun rule(active: Boolean) = RoutingRule(
        id = 1,
        departmentId = 2,
        managerId = 3,
        priority = 1,
        active = active,
    )

    private fun request(active: Boolean? = null) = UpdateRoutingRuleRequest(
        departmentId = 2,
        managerId = 3,
        priority = 1,
        active = active,
    )

    private fun prepare(rule: RoutingRule) {
        `when`(rules.findNotDeletedById(1)).thenReturn(Optional.of(rule))
        `when`(rules.saveAndFlush(rule)).thenReturn(rule)
        `when`(rules.existsActiveCombination(rule.departmentId, rule.projectId, rule.teamId, rule.id)).thenReturn(false)
        `when`(mapper.writeValueAsString(any())).thenReturn("{}")
    }

    @Test
    fun `update without active preserves current state`() {
        val rule = rule(active = true)
        prepare(rule)

        val result = service.update(1, request(), "admin")

        assertEquals(true, result.active)
    }

    @Test
    fun `update active false deactivates routing rule`() {
        val rule = rule(active = true)
        prepare(rule)

        val result = service.update(1, request(active = false), "admin")

        assertEquals(false, result.active)
    }

    @Test
    fun `update active true activates routing rule`() {
        val rule = rule(active = false)
        prepare(rule)

        val result = service.update(1, request(active = true), "admin")

        assertEquals(true, result.active)
    }
}
