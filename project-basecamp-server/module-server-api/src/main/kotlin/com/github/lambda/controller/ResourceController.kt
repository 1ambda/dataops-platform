package com.github.lambda.controller

import com.github.lambda.domain.entity.resource.ResourceEntity
import com.github.lambda.domain.service.ResourceService
import com.github.lambda.util.SecurityContext
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 리소스 컨트롤러
 */
@RestController
class ResourceController(
    private val resourceService: ResourceService,
) {
    /**
     * 리소스를 잠급니다.
     */
    @GetMapping("/api/resource/lock")
    fun lock(
        @RequestParam(defaultValue = "resource") resourceType: String,
    ): String {
        val userId = SecurityContext.getCurrentUserIdOrThrow()
        resourceService.saveResource(userId, resourceType)
        return resourceType
    }

    /**
     * 리소스를 해제합니다.
     */
    @GetMapping("/api/resource/release")
    fun release(): String {
        val userId = SecurityContext.getCurrentUserIdOrThrow()
        resourceService.deleteResource(userId)
        return "resource"
    }

    /**
     * 리소스 목록을 조회합니다.
     */
    @GetMapping("/api/resource")
    fun resources(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) userId: Long?,
        @PageableDefault(
            page = 0,
            size = 10,
            sort = ["createdAt"],
            direction = org.springframework.data.domain.Sort.Direction.DESC,
        )
        pageable: Pageable,
    ): List<ResourceEntity> = resourceService.getResources(keyword, userId, pageable.pageNumber, pageable.pageSize)
}
