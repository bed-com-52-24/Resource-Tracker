package com.resourcetracker.audit

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val correlationId = request.getHeader(AuditCorrelationContext.HEADER_NAME)
            ?.takeIf(AuditCorrelationContext::isValid)
            ?: UUID.randomUUID().toString()
        MDC.put(AuditCorrelationContext.MDC_KEY, correlationId)
        response.setHeader(AuditCorrelationContext.HEADER_NAME, correlationId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(AuditCorrelationContext.MDC_KEY)
        }
    }
}
