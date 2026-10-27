package com.resourcetracker.audit

import org.slf4j.MDC
import java.util.UUID

object AuditCorrelationContext {
    const val HEADER_NAME = "X-Correlation-ID"
    const val MDC_KEY = "correlationId"
    const val MAX_LENGTH = 64
    private val allowedPattern = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}")

    fun isValid(value: String): Boolean = allowedPattern.matches(value)

    fun currentOrNew(): String =
        MDC.get(MDC_KEY)?.takeIf(::isValid) ?: UUID.randomUUID().toString()

    fun <T> withCorrelationId(correlationId: String = UUID.randomUUID().toString(), block: () -> T): T {
        require(isValid(correlationId)) { "Invalid correlation ID" }
        val previous = MDC.get(MDC_KEY)
        MDC.put(MDC_KEY, correlationId)
        return try {
            block()
        } finally {
            if (previous == null) MDC.remove(MDC_KEY) else MDC.put(MDC_KEY, previous)
        }
    }
}
