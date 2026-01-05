package com.dataops.basecamp.domain.command.catalog

/**
 * Catalog filter parameters
 */
data class CatalogFilters(
    val project: String? = null,
    val dataset: String? = null,
    val owner: String? = null,
    val team: String? = null,
    val tags: Set<String> = emptySet(),
    val limit: Int = 50,
    val offset: Int = 0,
)
