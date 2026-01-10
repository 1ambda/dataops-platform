package com.dataops.basecamp.domain.repository.audit

import com.dataops.basecamp.common.enums.AuditAction
import com.dataops.basecamp.common.enums.AuditResource
import com.dataops.basecamp.domain.entity.audit.AuditLogEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime

/**
 * Audit Log Repository DSL Interface
 *
 * Defines complex query operations for AuditLogEntity using QueryDSL.
 */
interface AuditLogRepositoryDsl {
    /**
     * Find audit logs by user ID with pagination.
     */
    fun findByUserId(
        userId: String,
        pageable: Pageable,
    ): Page<AuditLogEntity>

    /**
     * Find audit logs by action with pagination.
     */
    fun findByAction(
        action: AuditAction,
        pageable: Pageable,
    ): Page<AuditLogEntity>

    /**
     * Find audit logs by resource with pagination.
     */
    fun findByResource(
        resource: AuditResource,
        pageable: Pageable,
    ): Page<AuditLogEntity>

    /**
     * Search audit logs with multiple filters.
     */
    fun search(
        userId: String?,
        action: AuditAction?,
        resource: AuditResource?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
        pageable: Pageable,
    ): Page<AuditLogEntity>

    /**
     * Get audit statistics.
     */
    fun getStats(
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
    ): AuditStats
}

/**
 * Audit Statistics Data Class
 */
data class AuditStats(
    val totalCount: Long,
    val actionCounts: Map<AuditAction, Long>,
    val resourceCounts: Map<AuditResource, Long>,
    val topUsers: List<UserAuditCount>,
    val avgDurationMs: Double,
)

/**
 * User Audit Count Data Class
 */
data class UserAuditCount(
    val userId: String,
    val count: Long,
)
