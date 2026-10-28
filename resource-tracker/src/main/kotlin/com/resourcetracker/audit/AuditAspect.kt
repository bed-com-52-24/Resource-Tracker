package com.resourcetracker.audit

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Pointcut
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.stereotype.Component
import org.springframework.transaction.interceptor.TransactionAspectSupport

@Aspect
@Component
class AuditAspect(
    private val auditService: AuditService,
    private val snapshotSerializer: AuditSnapshotSerializer
) {
    private val parser = SpelExpressionParser()

    @Around("@annotation(auditable)")
    fun auditMethod(joinPoint: ProceedingJoinPoint, auditable: Auditable): Any? {
        val args = joinPoint.args
        val context = StandardEvaluationContext().apply {
            args.forEachIndexed { index, arg ->
            setVariable("arg$index", arg) }
            // Also add named arguments if possible, but for simplicity we use arg0, arg1...
            // In a real scenario, we'd use LocalVariableTableParameterNameDiscoverer
        }

        // 1. Extract Item ID using SpEL
        val itemId = try {
            parser.parseExpression(auditable.itemIdExpression).getValue(context, String::class.java)
                ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }

        // 2. Capture state before (if applicable)
        // Note: To truly capture "before", we'd need the repository to fetch the entity.
        // Since we don't have the entity instance here, we'll rely on the service
        // to have performed the fetch, or we simply log the action.
        val previousState = null // Placeholder: automatic fetch would require repository access

        // 3. Execute the method
        val result = joinPoint.proceed()

        // 4. Capture state after
        // If the result is the entity, we use it. Otherwise, we attempt to find it in args.
        val newState = result ?: args.firstOrNull()

        // 5. Calculate diff
        val diff = AuditDiffUtils.diff(previousState, newState)

        // 6. Record the audit
        auditService.record(
            AuditRecord(
                action = auditable.action,
                itemType = auditable.itemType,
                itemId = itemId,
                previousState = previousState,
                newState = newState,
                changedFields = diff,
                comment = auditable.comment
            )
        )

        return result
    }
}
