package com.dataops.basecamp.domain.service

import com.dataops.basecamp.domain.entity.resource.ResourceEntity
import com.dataops.basecamp.domain.repository.resource.ResourceRepositoryDsl
import com.dataops.basecamp.domain.repository.resource.ResourceRepositoryJpa
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 리소스 서비스
 */
@Service
@Transactional
class ResourceService(
    private val resourceRepositoryJpa: ResourceRepositoryJpa,
    private val resourceRepositoryDsl: ResourceRepositoryDsl,
) {
    /**
     * 리소스를 저장합니다.
     */
    fun saveResource(
        userId: Long,
        resource: String,
    ) {
        val resourceEntity = ResourceEntity(userId, resource)
        resourceRepositoryJpa.save(resourceEntity)
    }

    /**
     * 사용자의 모든 리소스를 삭제합니다.
     */
    fun deleteResource(userId: Long) {
        resourceRepositoryJpa.deleteBulkByUserId(userId)
    }

    /**
     * 리소스 목록을 페이징으로 조회합니다.
     */
    fun getResources(
        keyword: String? = null,
        userId: Long? = null,
        page: Int = 0,
        size: Int = 20,
    ): List<ResourceEntity> =
        resourceRepositoryDsl.searchResourcesWithComplexConditions(
            userId,
            keyword,
            page,
            size,
        )
}
