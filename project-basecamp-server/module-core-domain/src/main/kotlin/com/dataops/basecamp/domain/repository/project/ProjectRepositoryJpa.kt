package com.dataops.basecamp.domain.repository.project

import com.dataops.basecamp.domain.entity.project.ProjectEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * Project Repository JPA 인터페이스 (순수 도메인 추상화)
 *
 * Project에 대한 기본 CRUD 작업을 정의합니다.
 * Pure Hexagonal Architecture 패턴에 따라 구현되었습니다.
 */
interface ProjectRepositoryJpa {
    // 기본 CRUD 작업
    fun save(project: ProjectEntity): ProjectEntity

    fun findById(id: Long): ProjectEntity?

    fun findByIdAndDeletedAtIsNull(id: Long): ProjectEntity?

    // 도메인 특화 조회 메서드 - 이름 기반
    fun findByName(name: String): ProjectEntity?

    fun findByNameAndDeletedAtIsNull(name: String): ProjectEntity?

    fun existsByName(name: String): Boolean

    fun existsByNameAndDeletedAtIsNull(name: String): Boolean

    // 전체 목록 조회 (페이지네이션) - soft delete 제외
    fun findAllByDeletedAtIsNullOrderByUpdatedAtDesc(pageable: Pageable): Page<ProjectEntity>

    // 삭제되지 않은 전체 개수
    fun countByDeletedAtIsNull(): Long
}
