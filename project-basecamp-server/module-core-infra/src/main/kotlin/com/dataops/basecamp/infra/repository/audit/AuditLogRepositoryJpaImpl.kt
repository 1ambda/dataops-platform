package com.dataops.basecamp.infra.repository.audit

import com.dataops.basecamp.domain.entity.audit.AuditLogEntity
import com.dataops.basecamp.domain.repository.audit.AuditLogRepositoryJpa
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Audit Log Repository JPA Implementation
 *
 * Implements AuditLogRepositoryJpa interface using Spring Data JPA.
 */
@Repository("auditLogRepositoryJpa")
interface AuditLogRepositoryJpaImpl :
    AuditLogRepositoryJpa,
    JpaRepository<AuditLogEntity, Long> {
    // Spring Data JPA auto-implements methods with naming convention

    override fun findByUserId(userId: String): List<AuditLogEntity>

    override fun findByTraceId(traceId: String): List<AuditLogEntity>
}
