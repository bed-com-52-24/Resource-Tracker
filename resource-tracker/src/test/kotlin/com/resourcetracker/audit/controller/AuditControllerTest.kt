package com.resourcetracker.audit.controller

import com.resourcetracker.audit.controller.AuditController
import com.resourcetracker.request.dto.RequestHistoryResponseDto
import com.resourcetracker.request.service.AuditRetrievalService
import com.resourcetracker.security.AuthenticatedUser
import com.resourcetracker.user.Role
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.LocalDate

class AuditControllerTest {
    private val audit = org.mockito.kotlin.mock<AuditRetrievalService>()
    private val auditEntries = org.mockito.kotlin.mock<com.resourcetracker.audit.AuditEntryQueryService>()
    private val caller = AuthenticatedUser(
        id = 8,
        username = "admin",
        role = Role.ADMIN,
        jti = "test-token",
        tokenExpiresAt = java.time.Instant.parse("2026-10-27T00:00:00Z"),
    )
    private val mvc: MockMvc = MockMvcBuilders.standaloneSetup(AuditController(audit, auditEntries))        .setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver())
        .build()

    @Test
    fun `actor route binds dates and authenticated role`() {
        whenever(audit.byActor(eq(12), eq(Role.ADMIN), eq(LocalDate.parse("2026-10-01")), eq(LocalDate.parse("2026-10-26")), any()))
            .thenReturn(PageImpl<RequestHistoryResponseDto>(emptyList(), PageRequest.of(0, 20), 0))

        performAsCaller(
            get("/api/v1/audit/actors/12")
                .param("from", "2026-10-01")
                .param("to", "2026-10-26"),
        ).andExpect(status().isOk)
    }

    @Test
    fun `item route binds item type and id`() {
        whenever(audit.byItem("LINE_ITEM", 55, Role.ADMIN)).thenReturn(emptyList())

        performAsCaller(
            get("/api/v1/audit/items/LINE_ITEM/55"),
        ).andExpect(status().isOk)
    }

    @Test
    fun `actions route binds repeated action parameters and date range`() {
        whenever(
            audit.byActions(
                eq(listOf("APPROVED", "ISSUED")),
                eq(Role.ADMIN),
                eq(LocalDate.parse("2026-10-01")),
                eq(LocalDate.parse("2026-10-26")),
                any(),
            ),
        ).thenReturn(PageImpl<RequestHistoryResponseDto>(emptyList(), PageRequest.of(0, 20), 0))

        performAsCaller(
            get("/api/v1/audit/actions")
                .param("actions", "APPROVED", "ISSUED")
                .param("from", "2026-10-01")
                .param("to", "2026-10-26"),
        ).andExpect(status().isOk)
    }

    private fun performAsCaller(request: MockHttpServletRequestBuilder): ResultActions {
        val previousContext = SecurityContextHolder.getContext()
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = UsernamePasswordAuthenticationToken(caller, null, emptyList())
        SecurityContextHolder.setContext(context)
        return try {
            mvc.perform(request)
        } finally {
            SecurityContextHolder.setContext(previousContext)
        }
    }
}
