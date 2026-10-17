package com.resourcetracker.common

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.springframework.data.jpa.domain.Specification
import java.time.LocalDate

data class ListSort(
    val field: String,
    val direction: String = "asc",
)

data class ListQuery(
    val filters: Map<String, List<String>> = emptyMap(),
    val keyword: String? = null,
    val sorts: List<ListSort> = emptyList(),
) {
    companion object {
        fun parse(
            params: Map<String, List<String>>,
            supportedFilters: Set<String>,
            supportedSortFields: Set<String>,
            keywordKeys: Set<String> = setOf("keyword", "search", "q"),
        ): ListQuery {
            val cleanFilters = linkedMapOf<String, List<String>>()

            for ((rawKey, rawValues) in params) {
                val key = rawKey.trim()
                if (key in keywordKeys || key !in supportedFilters) continue

                val values = rawValues
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                if (values.isEmpty()) continue
                if (values.size > 10) {
                    throw IllegalArgumentException("Invalid filter '$key': a filter may contain at most 10 values")
                }

                validateFilterValues(key, values)
                cleanFilters[key] = values
            }

            val keyword = params.entries
                .firstOrNull { it.key.trim() in keywordKeys }
                ?.value
                ?.joinToString(" ")
                ?.trim()
                ?.let { value ->
                    if (value.length > 200) {
                        throw IllegalArgumentException("Invalid filter 'keyword': keyword must be 200 characters or less")
                    }
                    if (value.length < 2) null else value
                }

            val sortValues = params["sort"].orEmpty()
            val parsedSorts = mutableListOf<ListSort>()
            if (sortValues.isNotEmpty()) {
                for (entry in sortValues) {
                    val trimmed = entry.trim()
                    if (trimmed.isEmpty()) continue
                    val parts = trimmed.split(":")
                    val field = parts.first().trim()
                    val direction = if (parts.size > 1) parts[1].trim().lowercase() else "asc"

                    if (parsedSorts.size >= 3) {
                        throw IllegalArgumentException(
                            "At most 3 sort fields are allowed. Allowed sort fields: ${supportedSortFields.sorted().joinToString()}"
                        )
                    }
                    if (field !in supportedSortFields) {
                        throw IllegalArgumentException(
                            "Invalid sort field '$field'. Allowed sort fields: ${supportedSortFields.sorted().joinToString()}"
                        )
                    }
                    if (direction !in setOf("asc", "desc")) {
                        throw IllegalArgumentException(
                            "Invalid sort direction '$direction' for '$field'. Use 'asc' or 'desc'."
                        )
                    }
                    parsedSorts += ListSort(field, direction)
                }
            } else if (supportedSortFields.isNotEmpty()) {
                parsedSorts += ListSort(supportedSortFields.first(), "asc")
            }

            return ListQuery(cleanFilters, keyword, parsedSorts)
        }

        private fun validateFilterValues(key: String, values: List<String>) {
            values.forEach { value ->
                when {
                    key.equals("active", ignoreCase = true) && value.lowercase() !in setOf("true", "false") ->
                        throw IllegalArgumentException("Invalid filter '$key': value '$value' must be either true or false")

                    (key.endsWith("Id", ignoreCase = true) || key.equals("id", ignoreCase = true)) && value.toIntOrNull() == null ->
                        throw IllegalArgumentException("Invalid filter '$key': value '$value' must be a valid integer")

                    isDateField(key) && runCatching { LocalDate.parse(value) }.isFailure ->
                        throw IllegalArgumentException("Invalid filter '$key': value '$value' must be a valid ISO date")
                }
            }
        }

        private fun isDateField(key: String): Boolean =
            key.contains("date", ignoreCase = true) ||
                key.endsWith("From", ignoreCase = true) ||
                key.endsWith("To", ignoreCase = true)
    }
}

object ListQuerySpec {
    fun <T : Any> specification(
        query: ListQuery,
        scope: ((Root<T>, CriteriaBuilder) -> Predicate?)? = null,
        fieldHandlers: Map<String, (Root<T>, CriteriaBuilder, List<String>) -> Predicate?> = emptyMap(),
        keywordHandler: ((Root<T>, CriteriaBuilder, String) -> Predicate?)? = null,
    ): Specification<T> = Specification<T> { root, _, cb ->
        val predicates = mutableListOf<Predicate>()

        scope?.invoke(root, cb)?.let { predicates += it }

        query.filters.forEach { (field, values) ->
            fieldHandlers[field]?.invoke(root, cb, values)?.let { predicates += it }
        }

        val keyword = query.keyword
        if (keyword != null && keywordHandler != null) {
            keywordHandler.invoke(root, cb, keyword)?.let { predicates += it }
        }

        if (predicates.isEmpty()) cb.conjunction() else cb.and(*predicates.toTypedArray())
    }
}
