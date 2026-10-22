package com.resourcetracker.common

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class CorrelationIdFilterTest {
    private val filter = CorrelationIdFilter()

    @Test
    fun `valid correlation ids are propagated to the request and response`() {
        val request = MockHttpServletRequest("GET", "/api/v1/resources").apply {
            addHeader(CorrelationIdFilter.HEADER_NAME, "trace-123")
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, FilterChain { _, _ -> })

        assertEquals("trace-123", request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE))
        assertEquals("trace-123", response.getHeader(CorrelationIdFilter.HEADER_NAME))
    }

    @Test
    fun `invalid correlation ids are replaced with a generated id`() {
        val request = MockHttpServletRequest("GET", "/api/v1/resources").apply {
            addHeader(CorrelationIdFilter.HEADER_NAME, "invalid id\n")
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, FilterChain { _, _ -> })

        val correlationId = response.getHeader(CorrelationIdFilter.HEADER_NAME)
            ?: error("Correlation ID response header was not set")
        assertTrue(correlationId.matches(Regex("[0-9a-f-]{36}")))
        assertNotEquals("invalid id\n", correlationId)
    }
}
