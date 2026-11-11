package com.resourcetracker.common

import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.server.ResponseStatusException

class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler()
    private val request = MockHttpServletRequest("POST", "/api/v1/requests").apply {
        addHeader("X-Correlation-ID", "issue-100-test")
    }

    @Test
    fun `unexpected errors return a safe message and reference id`() {
        val response = handler.unexpected(IllegalStateException("database password leaked"), request)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("INTERNAL_SERVER_ERROR", response.body?.code)
        assertTrue(response.body?.message.orEmpty().contains("contact support"))
        assertFalse(response.body?.message.orEmpty().contains("database password leaked"))
        assertEquals("issue-100-test", response.body?.correlationId)
        assertEquals("issue-100-test", response.headers.getFirst("X-Correlation-ID"))
    }

    @Test
    fun `invalid argument responses explain how to recover without exposing internals`() {
        val response = handler.illegalArgument(IllegalArgumentException("SQL constraint ck_resource_request"), request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("INVALID_REQUEST", response.body?.code)
        assertTrue(response.body?.message.orEmpty().contains("Check the accepted values"))
        assertFalse(response.body?.message.orEmpty().contains("SQL constraint"))
    }

    @Test
    fun `business rule response keeps its friendly message and status`() {
        val response = handler.api(BusinessRuleException("Team has no manager"), request)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("BUSINESS_RULE_VIOLATION", response.body?.code)
        assertEquals("Team has no manager", response.body?.message)
    }

    @Test
    fun `missing query parameter names the missing value`() {
        val exception = org.springframework.web.bind.MissingServletRequestParameterException("departmentId", "Int")

        val response = handler.missingParameter(exception, request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("MISSING_PARAMETER", response.body?.code)
        assertTrue(response.body?.message.orEmpty().contains("departmentId"))
    }

    @Test
    fun `response always includes request path and correlation id`() {
        val response = handler.status(ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"), request)

        assertEquals("/api/v1/requests", response.body?.path)
        assertNotNull(response.body?.timestamp)
        assertEquals("issue-100-test", response.body?.correlationId)
    }
}
