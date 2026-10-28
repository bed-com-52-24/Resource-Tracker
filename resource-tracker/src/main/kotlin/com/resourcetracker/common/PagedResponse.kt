package com.resourcetracker.common

/**
 * Standardized paginated response wrapper to avoid exposing
 * internal Spring Data Page metadata.
 */
data class PagedResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
) {
    companion object {
        fun <T : Any> from(page: org.springframework.data.domain.Page<T>): PagedResponse<T> {
            return PagedResponse(
                content = page.content,
                page = page.number,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages
            )
        }
    }
}
