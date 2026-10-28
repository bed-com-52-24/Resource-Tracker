package com.resourcetracker.common

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
        val correlationId = request.getHeader(HEADER_NAME)
            ?.takeIf(VALID_CORRELATION_ID::matches)
            ?: UUID.randomUUID().toString()

        request.setAttribute(REQUEST_ATTRIBUTE, correlationId)
        response.setHeader(HEADER_NAME, correlationId)
        MDC.put(MDC_KEY, correlationId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_KEY)
        }
    }

    companion object {
        const val HEADER_NAME = "X-Correlation-ID"
        const val REQUEST_ATTRIBUTE = "com.resourcetracker.correlationId"
        const val MDC_KEY = "correlationId"

        private val VALID_CORRELATION_ID = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}")
    }
}
