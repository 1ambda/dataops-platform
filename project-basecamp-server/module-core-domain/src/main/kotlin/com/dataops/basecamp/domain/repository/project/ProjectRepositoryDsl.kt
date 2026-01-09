package com.dataops.basecamp.domain.repository.project

import com.dataops.basecamp.domain.entity.project.ProjectEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * Project Repository DSL 인터페이스 (순수 도메인 추상화)
 *
 * Project에 대한 복잡한 쿼리 및 검색 작업을 정의합니다.
 * QueryDSL이나 기타 복잡한 쿼리 기술에 대한 추상화를 제공합니다.
 */
interface ProjectRepositoryDsl {
    /**
     * 복합 필터 조건으로 project 검색
     *
     * @param search 이름 및 displayName 검색 (부분 일치)
     * @param pageable 페이지네이션 정보
     * @return 필터 조건에 맞는 project 목록 (soft delete 제외)
     */
    fun findByFilters(
        search: String? = null,
        pageable: Pageable,
    ): Page<ProjectEntity>

    /**
     * 필터 조건에 맞는 project 개수 조회
     *
     * @param search 이름 및 displayName 검색 (부분 일치)
     * @return 조건에 맞는 project 개수 (soft delete 제외)
     */
    fun countByFilters(search: String? = null): Long
}
