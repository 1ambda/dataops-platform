package com.dataops.basecamp.domain.repository.audit

import com.dataops.basecamp.domain.entity.audit.AuditLogEntity

/**
 * Audit Log Repository JPA Interface
 *
 * Defines basic CRUD operations for AuditLogEntity.
 */
interface AuditLogRepositoryJpa {
    fun save(auditLog: AuditLogEntity): AuditLogEntity

    fun findById(id: Long): AuditLogEntity?

    fun findByUserId(userId: String): List<AuditLogEntity>

    fun findByTraceId(traceId: String): List<AuditLogEntity>
}
