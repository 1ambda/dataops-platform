package com.dataops.basecamp.domain.service

import com.dataops.basecamp.common.enums.AuditAction
import com.dataops.basecamp.common.enums.AuditResource
import com.dataops.basecamp.domain.entity.audit.AuditLogEntity
import com.dataops.basecamp.domain.repository.audit.AuditLogRepositoryDsl
import com.dataops.basecamp.domain.repository.audit.AuditLogRepositoryJpa
import com.dataops.basecamp.domain.repository.audit.AuditStats
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Audit Service
 *
 * Provides audit logging and retrieval functionality.
 */
@Service
@Transactional(readOnly = true)
class AuditService(
    private val auditLogRepositoryJpa: AuditLogRepositoryJpa,
    private val auditLogRepositoryDsl: AuditLogRepositoryDsl,
) {
    /**
     * Saves an audit log entry.
     */
    @Transactional
    fun saveLog(
        userId: String,
        userEmail: String?,
        traceId: String?,
        action: AuditAction,
        resource: AuditResource,
        httpMethod: String,
        requestUrl: String,
        pathVariables: String?,
        queryParameters: String?,
        requestBody: String? = null,
        responseStatus: Int,
        responseMessage: String?,
        durationMs: Long,
        clientType: String?,
        clientIp: String?,
        userAgent: String?,
        clientMetadata: String?,
        resourceId: String?,
        teamId: Long?,
    ): AuditLogEntity {
        val entity =
            AuditLogEntity(
                userId = userId,
                userEmail = userEmail,
                traceId = traceId,
                action = action,
                resource = resource,
                httpMethod = httpMethod,
                requestUrl = requestUrl,
                pathVariables = pathVariables,
                queryParameters = queryParameters,
                requestBody = requestBody,
                responseStatus = responseStatus,
                responseMessage = responseMessage,
                durationMs = durationMs,
                clientType = clientType,
                clientIp = clientIp,
                userAgent = userAgent,
                clientMetadata = clientMetadata,
                resourceId = resourceId,
                teamId = teamId,
            )
        return auditLogRepositoryJpa.save(entity)
    }

    /**
     * Finds an audit log by ID.
     */
    fun findById(id: Long): AuditLogEntity? = auditLogRepositoryJpa.findById(id)

    /**
     * Finds audit logs by user ID with pagination.
     */
    fun findByUserId(
        userId: String,
        pageable: Pageable,
    ): Page<AuditLogEntity> = auditLogRepositoryDsl.findByUserId(userId, pageable)

    /**
     * Finds audit logs by action with pagination.
     */
    fun findByAction(
        action: AuditAction,
        pageable: Pageable,
    ): Page<AuditLogEntity> = auditLogRepositoryDsl.findByAction(action, pageable)

    /**
     * Finds audit logs by resource with pagination.
     */
    fun findByResource(
        resource: AuditResource,
        pageable: Pageable,
    ): Page<AuditLogEntity> = auditLogRepositoryDsl.findByResource(resource, pageable)

    /**
     * Searches audit logs with multiple filters.
     */
    fun searchLogs(
        userId: String?,
        action: AuditAction?,
        resource: AuditResource?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
        pageable: Pageable,
    ): Page<AuditLogEntity> =
        auditLogRepositoryDsl.search(
            userId = userId,
            action = action,
            resource = resource,
            startDate = startDate,
            endDate = endDate,
            pageable = pageable,
        )

    /**
     * Gets audit statistics.
     */
    fun getStats(
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
    ): AuditStats = auditLogRepositoryDsl.getStats(startDate, endDate)
}
