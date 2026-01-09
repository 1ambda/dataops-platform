package com.dataops.basecamp.infra.repository.project

import com.dataops.basecamp.domain.entity.project.ProjectEntity
import com.dataops.basecamp.domain.repository.project.ProjectRepositoryJpa
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Project JPA Repository 구현 인터페이스
 *
 * Domain ProjectRepositoryJpa 인터페이스와 JpaRepository를 모두 확장합니다.
 * Pure Hexagonal Architecture 패턴에 따라 구현되었습니다.
 */
@Repository("projectRepositoryJpa")
interface ProjectRepositoryJpaImpl :
    ProjectRepositoryJpa,
    JpaRepository<ProjectEntity, Long> {
    // 기본 CRUD 작업 (save는 JpaRepository에서 자동 제공)

    override fun findByIdAndDeletedAtIsNull(id: Long): ProjectEntity?

    // 도메인 특화 조회 메서드 (Spring Data JPA auto-implements)
    override fun findByName(name: String): ProjectEntity?

    override fun findByNameAndDeletedAtIsNull(name: String): ProjectEntity?

    override fun existsByName(name: String): Boolean

    override fun existsByNameAndDeletedAtIsNull(name: String): Boolean

    // 전체 목록 조회 (페이지네이션) - soft delete 제외
    override fun findAllByDeletedAtIsNullOrderByUpdatedAtDesc(pageable: Pageable): Page<ProjectEntity>

    // 삭제되지 않은 전체 개수
    override fun countByDeletedAtIsNull(): Long
}
