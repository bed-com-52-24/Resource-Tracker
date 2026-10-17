package com.resourcetracker.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ListQueryTest {

    @Test
    fun `AND across fields and OR repeated values are preserved`() {
        val parsed = ListQuery.parse(
            params = mapOf(
                "status" to listOf("ACTIVE", "INACTIVE"),
                "departmentId" to listOf("7"),
            ),
            supportedFilters = setOf("status", "departmentId"),
            supportedSortFields = setOf("name", "status"),
        )

        assertEquals(setOf("status", "departmentId"), parsed.filters.keys)
        assertEquals(listOf("ACTIVE", "INACTIVE"), parsed.filters["status"])
        assertEquals(listOf("7"), parsed.filters["departmentId"])
        assertNull(parsed.keyword)
    }

    @Test
    fun `unsupported filters are ignored and invalid values are rejected with reason`() {
        val ex = assertThrows<IllegalArgumentException> {
            ListQuery.parse(
                params = mapOf(
                    "status" to listOf("ACTIVE"),
                    "otherThing" to listOf("abc"),
                    "departmentId" to listOf("not-a-number"),
                ),
                supportedFilters = setOf("status", "departmentId"),
                supportedSortFields = setOf("name"),
            )
        }

        val msg = ex.message.orEmpty()
        assertEquals(true, msg.contains("departmentId"))
        assertEquals(true, msg.contains("integer"))
    }

    @Test
    fun `keyword is trimmed, ignored when too short, and rejected when too long`() {
        val parsed = ListQuery.parse(
            params = mapOf("keyword" to listOf("  ai  ")),
            supportedFilters = setOf("keyword"),
            supportedSortFields = setOf("name"),
        )
        assertEquals("ai", parsed.keyword)

        val short = ListQuery.parse(
            params = mapOf("keyword" to listOf("a")),
            supportedFilters = setOf("keyword"),
            supportedSortFields = setOf("name"),
        )
        assertNull(short.keyword)

        val tooLong = "x".repeat(201)
        val ex = assertThrows<IllegalArgumentException> {
            ListQuery.parse(
                params = mapOf("keyword" to listOf(tooLong)),
                supportedFilters = setOf("keyword"),
                supportedSortFields = setOf("name"),
            )
        }
        assertEquals(true, ex.message.orEmpty().contains("200"))
    }

    @Test
    fun `sort fields are limited and default to asc`() {
        val parsed = ListQuery.parse(
            params = mapOf("sort" to listOf("name:desc", "status", "createdAt:desc", "unknown")),
            supportedFilters = emptySet(),
            supportedSortFields = setOf("name", "status", "createdAt"),
        )

        assertEquals(listOf("name", "status", "createdAt"), parsed.sorts.map { it.field })
        assertEquals(listOf("desc", "asc", "desc"), parsed.sorts.map { it.direction })

        val ex = assertThrows<IllegalArgumentException> {
            ListQuery.parse(
                params = mapOf("sort" to listOf("password")),
                supportedFilters = emptySet(),
                supportedSortFields = setOf("name"),
            )
        }
        assertEquals(true, ex.message.orEmpty().contains("Allowed sort fields"))
    }
}
