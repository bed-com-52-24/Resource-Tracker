package com.resourcetracker.audit

import org.springframework.core.annotation.AliasFor
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Mark a method for automatic auditing.
 * The aspect will attempt to capture the state of the target entity before and after the method call.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
annotation class Auditable(
    val action: AuditAction,
    val itemType: String,
    val itemIdExpression: String = "#id", // SpEL expression to find the item ID in method arguments
    val comment: String = ""
)
