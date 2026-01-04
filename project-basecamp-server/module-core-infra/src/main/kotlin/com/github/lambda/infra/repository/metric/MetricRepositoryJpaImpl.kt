package com.github.lambda.infra.repository.metric

import com.github.lambda.domain.entity.metric.MetricEntity
import com.github.lambda.domain.repository.metric.MetricRepositoryJpa
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Metric JPA Repository Implementation Interface
 *
 * Implements the domain MetricRepositoryJpa interface by extending JpaRepository directly.
 * This simplified pattern combines domain interface and Spring Data JPA into one interface.
 * Follows Pure Hexagonal Architecture pattern (same as ResourceRepositoryJpaImpl).
 */
@Repository("metricRepositoryJpa")
interface MetricRepositoryJpaImpl :
    MetricRepositoryJpa,
    JpaRepository<MetricEntity, Long> {
    // Domain-specific queries (Spring Data JPA auto-implements these)
    override fun findByName(name: String): MetricEntity?

    override fun existsByName(name: String): Boolean

    override fun findByOwner(owner: String): List<MetricEntity>

    override fun countByOwner(owner: String): Long
}
