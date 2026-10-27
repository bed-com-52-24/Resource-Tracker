package com.resourcetracker.audit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class AuditSnapshotSerializerTest {
    private val mapper = JsonMapper.builder().build()
    private val serializer = AuditSnapshotSerializer(mapper)

    @Test
    fun `redacts sensitive keys recursively without changing other values`() {
        val serialized = serializer.serialize(
            mapOf(
                "username" to "person",
                "password_hash" to "password-hash-value",
                "authConfig" to mapOf(
                    "apiKey" to "api-secret",
                    "items" to listOf(
                        mapOf("access-token" to "access-secret", "displayName" to "visible"),
                    ),
                ),
                "authorizationHeader" to "Bearer secret",
            ),
        )

        val result = mapper.readTree(requireNotNull(serialized))
        assertEquals("person", result["username"].asString())
        assertEquals("***", result["password_hash"].asString())
        assertEquals("***", result["authConfig"]["apiKey"].asString())
        assertEquals("***", result["authConfig"]["items"][0]["access-token"].asString())
        assertEquals("visible", result["authConfig"]["items"][0]["displayName"].asString())
        assertEquals("***", result["authorizationHeader"].asString())
        assertTrue(!serialized.contains("password-hash-value"))
        assertTrue(!serialized.contains("api-secret"))
        assertTrue(!serialized.contains("access-secret"))
        assertTrue(!serialized.contains("secret"))
    }

    @Test
    fun `null state is stored as SQL null`() {
        assertEquals(null, serializer.serialize(null))
    }
}
