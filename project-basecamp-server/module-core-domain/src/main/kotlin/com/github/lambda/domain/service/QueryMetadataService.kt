package com.github.lambda.domain.service

import com.github.lambda.domain.command.query.CancelQueryCommand
import com.github.lambda.domain.model.query.QueryExecutionEntity
import com.github.lambda.domain.model.query.QueryScope
import com.github.lambda.domain.model.query.QueryStatus
import com.github.lambda.domain.query.query.ListQueriesQuery
import com.github.lambda.domain.query.query.QueryScopeFilter
import com.github.lambda.domain.repository.QueryExecutionRepositoryDsl
import com.github.lambda.domain.repository.QueryExecutionRepositoryJpa
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

/**
 * Query Metadata Service
 *
 * Pure Hexagonal Architecture pattern implementation for query metadata management.
 * - Services are concrete classes (no interfaces)
 * - Commands and queries are clearly separated
 * - Domain Entity is returned directly (DTO conversion is handled at API layer)
 * - Business logic and data access logic separation
 */
@Service
@Transactional(readOnly = true) // Default is read-only
class QueryMetadataService(
    private val queryExecutionRepositoryJpa: QueryExecutionRepositoryJpa,
    private val queryExecutionRepositoryDsl: QueryExecutionRepositoryDsl,
) {
    // === Command Processing ===

    /**
     * Cancel a running query
     *
     * @param command Cancel query command
     * @param currentUser Current user identifier
     * @return Updated query execution entity
     * @throws QueryNotFoundException if query is not found
     * @throws QueryNotCancellableException if query cannot be cancelled
     * @throws AccessDeniedException if user doesn't have permission
     */
    @Transactional
    fun cancelQuery(
        command: CancelQueryCommand,
        currentUser: String,
    ): QueryExecutionEntity {
        val query =
            queryExecutionRepositoryJpa
                .findById(command.queryId)
                .orElseThrow { QueryNotFoundException(command.queryId) }

        // Validate cancellation permissions
        validateCancelAccess(query, currentUser)

        // Check if cancellable
        if (!query.isCancellable()) {
            throw QueryNotCancellableException(
                queryId = command.queryId,
                currentStatus = query.status,
            )
        }

        // Update query status
        val now = Instant.now()
        query.status = QueryStatus.CANCELLED
        query.cancelledBy = currentUser
        query.cancelledAt = now
        query.cancellationReason = command.reason
        query.completedAt = now

        val updatedQuery = queryExecutionRepositoryJpa.save(query)

        return updatedQuery
    }

    // === Query Processing ===

    /**
     * List query executions with filtering
     *
     * @param query List queries query parameters
     * @param currentUser Current user identifier
     * @return List of query executions matching the criteria
     */
    fun listQueries(
        query: ListQueriesQuery,
        currentUser: String,
    ): List<QueryExecutionEntity> {
        val filter = buildScopeFilter(query.scope, currentUser)

        return queryExecutionRepositoryDsl.findByScope(
            filter = filter,
            status = query.status,
            startDate = query.startDate,
            endDate = query.endDate,
            limit = query.limit,
            offset = query.offset,
        )
    }

    /**
     * Get query execution details by ID
     *
     * @param queryId Query execution ID
     * @param currentUser Current user identifier
     * @return Query execution entity or null if not found
     * @throws AccessDeniedException if user doesn't have permission
     */
    fun getQueryDetails(
        queryId: String,
        currentUser: String,
    ): QueryExecutionEntity? {
        val queryOpt = queryExecutionRepositoryJpa.findById(queryId)

        if (queryOpt.isEmpty) {
            return null
        }

        val query = queryOpt.get()
        // Validate access permissions
        validateViewAccess(query, currentUser)

        return query
    }

    /**
     * Get query execution details by ID (throws exception if not found)
     *
     * @param queryId Query execution ID
     * @param currentUser Current user identifier
     * @return Query execution entity
     * @throws QueryNotFoundException if query is not found
     * @throws AccessDeniedException if user doesn't have permission
     */
    fun getQueryDetailsOrThrow(
        queryId: String,
        currentUser: String,
    ): QueryExecutionEntity = getQueryDetails(queryId, currentUser) ?: throw QueryNotFoundException(queryId)

    /**
     * Count queries by filter criteria
     *
     * @param query List queries query parameters
     * @param currentUser Current user identifier
     * @return Count of queries matching the criteria
     */
    fun countQueries(
        query: ListQueriesQuery,
        currentUser: String,
    ): Long {
        val filter = buildScopeFilter(query.scope, currentUser)

        return queryExecutionRepositoryDsl.countByFilter(
            submittedBy = filter.submittedBy,
            isSystemQuery = filter.isSystemQuery,
            status = query.status,
            startDate = query.startDate,
            endDate = query.endDate,
        )
    }

    /**
     * Get running queries for a user
     *
     * @param currentUser Current user identifier (null for all users - requires admin role)
     * @return List of running queries
     */
    fun getRunningQueries(currentUser: String?): List<QueryExecutionEntity> =
        queryExecutionRepositoryDsl.findRunningQueries(currentUser)

    // === Access Control Logic ===

    /**
     * Build query scope filter based on user permissions
     */
    private fun buildScopeFilter(
        scope: QueryScope,
        currentUser: String,
    ): QueryScopeFilter =
        when (scope) {
            QueryScope.MY ->
                QueryScopeFilter(
                    submittedBy = currentUser,
                    isSystemQuery = false,
                )
            QueryScope.SYSTEM ->
                QueryScopeFilter(
                    isSystemQuery = true,
                )
            QueryScope.USER ->
                QueryScopeFilter(
                    submittedBy = currentUser, // For now, same as MY. Could be extended for admin role
                )
            QueryScope.ALL -> QueryScopeFilter() // No filter - requires admin role validation
        }

    /**
     * Validate view access for a query
     */
    private fun validateViewAccess(
        query: QueryExecutionEntity,
        currentUser: String,
    ) {
        when {
            query.submittedBy == currentUser -> {
                // Owner can always view
            }
            query.isSystemQuery -> {
                // System queries are viewable by all (for now)
            }
            else -> {
                // For now, users can only view their own queries
                // TODO: Implement role-based access control
                throw AccessDeniedException("Cannot view other users' queries")
            }
        }
    }

    /**
     * Validate cancel access for a query
     */
    private fun validateCancelAccess(
        query: QueryExecutionEntity,
        currentUser: String,
    ) {
        when {
            query.submittedBy == currentUser -> {
                // Owner can always cancel
            }
            query.isSystemQuery -> {
                // System queries require special permission (for now, deny)
                throw AccessDeniedException("Cannot cancel system queries")
            }
            else -> {
                // Other users' queries require admin permission (for now, deny)
                throw AccessDeniedException("Cannot cancel other users' queries")
            }
        }
    }
}

// === Exceptions ===

class QueryNotFoundException(
    queryId: String,
) : RuntimeException("Query not found: $queryId")

class QueryNotCancellableException(
    val queryId: String,
    val currentStatus: QueryStatus,
) : RuntimeException("Query $queryId cannot be cancelled. Current status: $currentStatus")

class AccessDeniedException(
    message: String,
) : RuntimeException(message)
