package com.github.lambda.domain.model.query

import com.github.lambda.common.enums.QueryScope
import com.github.lambda.common.enums.QueryStatus
import java.time.LocalDate

/**
 * Query to list query executions with filters
 */
data class ListQueriesQuery(
    val scope: QueryScope = QueryScope.MY,
    val status: QueryStatus? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val limit: Int = 50,
    val offset: Int = 0,
)

/**
 * Query scope filter for database queries
 */
data class QueryScopeFilter(
    val submittedBy: String? = null,
    val isSystemQuery: Boolean? = null,
)
