package com.github.lambda.domain.repository

import com.github.lambda.domain.model.query.QueryExecutionEntity
import com.github.lambda.domain.model.query.QueryStatus
import com.github.lambda.domain.query.query.QueryScopeFilter
import java.time.LocalDate

/**
 * Repository interface for complex QueryExecution queries using QueryDSL
 */
interface QueryExecutionRepositoryDsl {
    /**
     * Find query executions by filter criteria
     */
    fun findByFilter(
        submittedBy: String? = null,
        isSystemQuery: Boolean? = null,
        status: QueryStatus? = null,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): List<QueryExecutionEntity>

    /**
     * Find query executions by scope filter
     */
    fun findByScope(
        filter: QueryScopeFilter,
        status: QueryStatus? = null,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): List<QueryExecutionEntity>

    /**
     * Count query executions by filter criteria
     */
    fun countByFilter(
        submittedBy: String? = null,
        isSystemQuery: Boolean? = null,
        status: QueryStatus? = null,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
    ): Long

    /**
     * Find running queries by user
     */
    fun findRunningQueries(submittedBy: String? = null): List<QueryExecutionEntity>
}
