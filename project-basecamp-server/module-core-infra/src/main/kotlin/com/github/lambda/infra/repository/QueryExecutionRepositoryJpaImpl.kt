package com.github.lambda.infra.repository

import com.github.lambda.domain.entity.query.QueryExecutionEntity
import com.github.lambda.domain.repository.QueryExecutionRepositoryJpa
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * QueryExecution JPA Repository Implementation Interface
 *
 * Implements the domain QueryExecutionRepositoryJpa interface by extending JpaRepository directly.
 * This simplified pattern combines domain interface and Spring Data JPA into one interface.
 * Follows Pure Hexagonal Architecture pattern.
 */
@Repository("queryExecutionRepositoryJpa")
interface QueryExecutionRepositoryJpaImpl :
    QueryExecutionRepositoryJpa,
    JpaRepository<QueryExecutionEntity, String> {
    // Domain-specific queries (Spring Data JPA auto-implements these)
    override fun findBySubmittedBy(submittedBy: String): List<QueryExecutionEntity>
}
