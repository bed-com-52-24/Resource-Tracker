package com.resourcetracker.common

import com.resourcetracker.user.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler()

    @Test
    fun `api errors use the standard response contract`() {
        val request = MockHttpServletRequest("GET", "/api/v1/resources/9").apply {
            setAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE, "trace-123")
        }

        val response = handler.api(NotFoundException("Resource not found"), request)

        assertEquals(404, response.statusCode.value())
        assertNotNull(response.body?.timestamp)
        assertEquals("Not Found", response.body?.error)
        assertEquals("NOT_FOUND", response.body?.code)
        assertEquals("Resource not found", response.body?.message)
        assertEquals("/api/v1/resources/9", response.body?.path)
        assertEquals("trace-123", response.body?.correlationId)
        assertEquals(emptyList<String>(), response.body?.details)
    }

    @Test
    fun `forbidden role errors disclose required and actual roles`() {
        val request = MockHttpServletRequest("POST", "/api/v1/resources")

        val response = handler.api(
            ForbiddenRoleException(listOf(Role.ADMIN), Role.TEAM_LEAD),
            request,
        )

        assertEquals(403, response.statusCode.value())
        assertEquals(
            listOf("Required role: ADMIN", "Caller role: TEAM_LEAD"),
            response.body?.details,
        )
        assertEquals("Access requires ADMIN; caller role is TEAM_LEAD", response.body?.message)
    }
}
