package com.dataops.basecamp.domain.repository.catalog

import com.dataops.basecamp.domain.projection.catalog.SampleQuery

/**
 * Sample Query Repository Interface (Domain Port)
 *
 * Defines operations for managing sample queries associated with tables.
 */
interface SampleQueryRepositoryDsl {
    /**
     * Find sample queries for a table
     *
     * @param tableRef Fully qualified table reference
     * @param limit Maximum queries to return
     * @return List of sample queries sorted by run count (descending)
     */
    fun findByTableRef(
        tableRef: String,
        limit: Int,
    ): List<SampleQuery>

    /**
     * Save a sample query for a table
     *
     * @param tableRef Fully qualified table reference
     * @param query Sample query to save
     * @return Saved sample query
     */
    fun save(
        tableRef: String,
        query: SampleQuery,
    ): SampleQuery

    /**
     * Increment run count for a sample query
     *
     * @param tableRef Fully qualified table reference
     * @param title Query title to identify
     */
    fun incrementRunCount(
        tableRef: String,
        title: String,
    )
}
