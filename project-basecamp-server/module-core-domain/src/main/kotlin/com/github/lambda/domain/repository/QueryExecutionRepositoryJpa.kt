package com.github.lambda.domain.repository

import com.github.lambda.domain.entity.query.QueryExecutionEntity
import java.util.*

/**
 * Repository interface for basic QueryExecution CRUD operations
 */
interface QueryExecutionRepositoryJpa {
    /**
     * Save a query execution
     */
    fun save(queryExecution: QueryExecutionEntity): QueryExecutionEntity

    /**
     * Find a query execution by ID
     */
    fun findById(queryId: String): Optional<QueryExecutionEntity>

    /**
     * Check if a query execution exists by ID
     */
    fun existsById(queryId: String): Boolean

    /**
     * Delete a query execution by ID
     */
    fun deleteById(queryId: String)

    /**
     * Find all query executions by submission user
     */
    fun findBySubmittedBy(submittedBy: String): List<QueryExecutionEntity>
}
