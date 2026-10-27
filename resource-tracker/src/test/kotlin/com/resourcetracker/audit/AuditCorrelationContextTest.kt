package com.resourcetracker.audit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class AuditCorrelationContextTest {
    @Test
    fun `operation context is restored after completion`() {
        assertNull(MDC.get(AuditCorrelationContext.MDC_KEY))

        val result = AuditCorrelationContext.withCorrelationId("operation-123") {
            assertEquals("operation-123", MDC.get(AuditCorrelationContext.MDC_KEY))
            AuditCorrelationContext.currentOrNew()
        }

        assertEquals("operation-123", result)
        assertNull(MDC.get(AuditCorrelationContext.MDC_KEY))
    }

    @Test
    fun `invalid incoming correlation ids are rejected`() {
        assertTrue(AuditCorrelationContext.isValid("trace-id:123"))
        assertTrue(!AuditCorrelationContext.isValid("bad id"))
        assertTrue(!AuditCorrelationContext.isValid("x".repeat(AuditCorrelationContext.MAX_LENGTH + 1)))
    }

    @Test
    fun `filter propagates valid ids and clears MDC after request`() {
        val request = MockHttpServletRequest().apply {
            addHeader(AuditCorrelationContext.HEADER_NAME, "trace:abc-123")
        }
        val response = MockHttpServletResponse()
        val filter = CorrelationIdFilter()

        filter.doFilter(request, response) { _, _ ->
            assertEquals("trace:abc-123", MDC.get(AuditCorrelationContext.MDC_KEY))
        }

        assertEquals("trace:abc-123", response.getHeader(AuditCorrelationContext.HEADER_NAME))
        assertNull(MDC.get(AuditCorrelationContext.MDC_KEY))
    }

    @Test
    fun `filter replaces invalid ids with generated ids`() {
        val request = MockHttpServletRequest().apply {
            addHeader(AuditCorrelationContext.HEADER_NAME, "invalid correlation id")
        }
        val response = MockHttpServletResponse()

        CorrelationIdFilter().doFilter(request, response) { _, _ -> }

        val correlationId = requireNotNull(response.getHeader(AuditCorrelationContext.HEADER_NAME))
        assertTrue(AuditCorrelationContext.isValid(correlationId))
    }
}
