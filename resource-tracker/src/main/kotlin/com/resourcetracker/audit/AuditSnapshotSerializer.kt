package com.resourcetracker.audit

import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

@Component
class AuditSnapshotSerializer(private val objectMapper: ObjectMapper) {
    fun serialize(value: Any?): String? {
        if (value == null) return null
        return objectMapper.writeValueAsString(redact(objectMapper.valueToTree<JsonNode>(value)))
    }

    private fun redact(node: JsonNode): JsonNode = when {
        node.isObject -> {
            val result = objectMapper.createObjectNode()
            node.properties().forEach { (name, child) ->
                result.set(
                    name,
                    if (isSensitiveName(name)) objectMapper.nodeFactory.textNode("***") else redact(child),
                )
            }
            result
        }
        node.isArray -> {
            val result = objectMapper.createArrayNode()
            node.forEach { result.add(redact(it)) }
            result
        }
        else -> node
    }

    private fun isSensitiveName(name: String): Boolean {
        val normalized = name.filter(Char::isLetterOrDigit).lowercase()
        return listOf(
            "password", "passwordhash", "token", "secret", "apikey", "key",
            "privatekey", "authorization", "credential",
        ).any(normalized::contains)
    }
}
