package com.dataops.basecamp.domain.service

import com.dataops.basecamp.common.enums.AuditResourceAction
import com.dataops.basecamp.domain.entity.audit.AuditAccessEntity
import com.dataops.basecamp.domain.entity.audit.AuditResourceEntity
import com.dataops.basecamp.domain.repository.audit.AuditAccessRepositoryJpa
import com.dataops.basecamp.domain.repository.audit.AuditResourceRepositoryJpa
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 감사 서비스
 */
@Service
@Transactional
class AuditService(
    private val auditAccessRepository: AuditAccessRepositoryJpa,
    private val auditResourceRepository: AuditResourceRepositoryJpa,
) {
    /**
     * 리소스 감사 정보를 저장합니다.
     */
    fun saveAuditResource(
        userId: Long,
        resourceType: String,
        resourceId: Long,
        action: AuditResourceAction,
    ) {
        val auditResourceEntity =
            AuditResourceEntity(
                userId = userId,
                resourceType = resourceType,
                resourceId = resourceId,
                resourceContent = null,
                action = action,
            )

        auditResourceRepository.save(auditResourceEntity)
    }

    /**
     * 접근 감사 정보를 저장합니다.
     */
    fun saveAuditAccess(
        userId: Long,
        accessType: String,
        accessContent: String?,
        action: AuditResourceAction,
    ) {
        val auditAccessEntity =
            AuditAccessEntity(
                userId = userId,
                accessType = accessType,
                accessContent = accessContent,
                action = action,
            )

        auditAccessRepository.save(auditAccessEntity)
    }
}
