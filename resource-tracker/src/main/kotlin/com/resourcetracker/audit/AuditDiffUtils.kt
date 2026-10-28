package com.resourcetracker.audit

import java.util.Objects

object AuditDiffUtils {
    /**
     * Compares two objects and returns a map of fields that have changed.
     * Only includes fields where the values are different.
     */
    fun <T : Any> diff(old: T?, new: T?): Map<String, Any?> {
        if (old == null && new == null) return emptyMap()
        if (old == null) return mapOf("state" to "CREATED")
        if (new == null) return mapOf("state" to "DELETED")

        val changes = mutableMapOf<String, Any?>()

        // Use reflection to compare fields
        old::class.java.declaredFields.forEach { field ->
            field.isAccessible = true
            val oldVal = field.get(old)
            val newVal = field.get(new)

            if (!Objects.equals(oldVal, newVal)) {
                changes[field.name] = mapOf(
                    "from" to oldVal,
                    "to" to newVal
                )
            }
        }

        return changes
    }
}
